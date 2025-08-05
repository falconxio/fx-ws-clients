//go:build ws_order_client
// +build ws_order_client

package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"strconv"
	"strings"
	"time"

	"github.com/falconxio/falcon/utils"

	"github.com/gorilla/websocket"
)

var filled int = 0
var acks int = 0

type TokenPair struct {
	BaseToken  string `json:"base_token"`
	QuoteToken string `json:"quote_token"`
}

func (t *TokenPair) Repr() string {
	return fmt.Sprintf("%s/%s", t.BaseToken, t.QuoteToken)
}

type TokenValue struct {
	Token string  `json:"token"`
	Value float64 `json:"value"`
}

type ResponseMessage struct {
	Status    string      `json:"status" default:"error"`
	Error     interface{} `json:"error,omitempty"`
	Event     string      `json:"event"`
	RequestId string      `json:"request_id"`
	Body      interface{} `json:"body,omitempty"`
}

type OrderFill struct {
	FxQuoteId string     `json:"fx_quote_id"`
	Quantity  TokenValue `json:"quantity"`
	Price     float64    `json:"price"`
	TExecute  time.Time  `json:"t_execute"`
}

type OrderResponse struct {
	ClientOrderID string      `json:"client_order_id"`
	OrderID       string      `json:"order_id"`
	OrderStatus   string      `json:"order_status"`
	TokenPair     TokenPair   `json:"token_pair"`
	Quantity      TokenValue  `json:"quantity"`
	LimitPrice    *float64    `json:"limit_price,omitempty"`
	ExecutedPrice *float64    `json:"executed_price,omitempty"`
	Side          *string     `json:"side"`
	OrderType     *string     `json:"order_type"`
	TimeInForce   *string     `json:"time_in_force"`
	ExpiryTime    *time.Time  `json:"expiry_time,omitempty"`
	Fills         []OrderFill `json:"fills"`
	Error         interface{} `json:"error,omitempty"`
}

// Utility Functions:
func getSign(timeint int64, secretKey string, path string) string {
	timestamp := strconv.FormatInt(timeint, 10)
	method := "GET"

	message := strings.Join([]string{timestamp, method, path, ""}, "")
	signature := GetSignedMessageForKey([]byte(message), secretKey)
	return signature
}

func GetSignedMessageForKey(message []byte, secretKey string) string {
	key, _ := base64.StdEncoding.DecodeString(secretKey)
	mac := hmac.New(sha256.New, key)
	mac.Write(message)
	return base64.StdEncoding.EncodeToString(mac.Sum(nil))
}

type FalconxWSClient struct {
	Host                string
	Path                string
	SSL                 bool
	apiKey              string
	secret              string
	passphrase          string
	Conn                *websocket.Conn
	RetryOnError        bool
	NumRetries          *uint64
	RetryDelay          time.Duration
	retryCount          uint64
	authenticated       bool
	LogStreams          bool
	readerActive        bool
	responseChan        chan bool
	interruptRead       chan bool
	OrderIdFromResponse chan string
}

func NewFalconxWSClient(host string, path string) *FalconxWSClient {
	return &FalconxWSClient{
		Host:                host,
		Path:                path,
		interruptRead:       make(chan bool),
		responseChan:        make(chan bool, 10),
		OrderIdFromResponse: make(chan string, 10),
		LogStreams:          true,
	}
}

func (fws *FalconxWSClient) DisableLogging() {
	fws.LogStreams = false
}

func (fws *FalconxWSClient) EnableSSL() {
	fws.SSL = true
}

func (fws *FalconxWSClient) SetAuth(apiKey string, secret string, passphrase string) {
	fws.apiKey = apiKey
	fws.secret = secret
	fws.passphrase = passphrase
}

func (fws *FalconxWSClient) EnableRetry(retryDelayInSeconds uint64, numOfRetries *uint64) {
	fws.RetryOnError = true
	if retryDelayInSeconds > 0 {
		fws.RetryDelay = time.Second * time.Duration(retryDelayInSeconds)
	} else {
		fws.RetryDelay = time.Second * 1
	}
	fws.NumRetries = numOfRetries
}

func (fws *FalconxWSClient) Connect() {
	dialer := websocket.DefaultDialer
	dialer.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}

	var url string
	if fws.SSL {
		url = fmt.Sprintf("%s://%s%s", "wss", fws.Host, fws.Path)
	} else {
		url = fmt.Sprintf("%s://%s%s", "ws", fws.Host, fws.Path)
	}

	log.Println("Trying to connect to ", url)
	conn, a, err := dialer.Dial(url, nil)

	if err != nil {
		log.Print("Error connecting to WebSocket server:", a, err)
		if fws.RetryOnError && (fws.NumRetries == nil || fws.retryCount < *fws.NumRetries) {
			log.Printf("Retrying in %+v", fws.RetryDelay)
			time.Sleep(fws.RetryDelay)
			fws.Connect()
		}
		return
	} else {
		log.Printf("Connection successful")
	}

	fws.Conn = conn
	if !fws.readerActive {
		go fws.ReadMessages()
	}
	// defer conn.Close()
}

func (fws *FalconxWSClient) Authenticate() (bool, error) {
	timestamp := time.Now().Unix()

	signature := getSign(timestamp, fws.secret, fws.Path)

	req := map[string]interface{}{
		"action":     "auth",
		"api_key":    fws.apiKey,
		"passphrase": fws.passphrase,
		"signature":  signature,
		"timestamp":  timestamp,
		"request_id": "my_request",
	}
	err := fws.Conn.WriteJSON(req)

	if err != nil {
		log.Println("Error: ", err)
		return false, err
	}
	log.Println("Sent Auth msg")
	return <-fws.responseChan, nil
}

func (fws *FalconxWSClient) SendOrderRequest() {
	clOid := utils.GenerateId(32)
	req := map[string]interface{}{
		"action":     "create_order_request",
		"request_id": "my_request_id_1234",
		"order_type": "limit",
		"order_details": map[string]interface{}{
			"client_order_id": clOid, // "unique_client_order_id321840",
			"base_token":      "ETH", //"USDT",
			"quote_token":     "USD",
			"quantity":        0.01,  //30,
			"quantity_token":  "ETH", //"USDT",
			"limit_price":     3000,
			"side":            "sell",
			"tif":             "gtx",
			"expiry":          time.Now().Add(time.Hour * 1),
		},
	}

	tStart := time.Now()
	err := fws.Conn.WriteJSON(req)
	tEnd := time.Now()
	log.Println("Time taken to send order request: ", tEnd.Sub(tStart))
	if err != nil {
		log.Println("Error: ", err)
	}
	log.Println("Sent Create Order msg")

	<-fws.OrderIdFromResponse
}

func (fws *FalconxWSClient) SendOrderUpdateRequest(orderId string, clOid string) {
	nclOid := utils.GenerateId(16)
	UpdateReq := map[string]interface{}{
		"action":     "update_order_request",
		"request_id": "my_request_id_1234",
		"order_type": "limit",
		"order_details": map[string]interface{}{
			"orig_client_order_id": clOid,
			"client_order_id":      nclOid,
			"order_id":             orderId, //mandatory
			"base_token":           "ETH",
			"quote_token":          "USD",
			"quantity":             0.0004,
			"quantity_token":       "ETH",
			"limit_price":          80001,
			"side":                 "sell",
			// "tif":                  "gtx",
			// "expiry":               time.Now().Add(time.Hour * 21 * 24),
		},
	}
	log.Println(UpdateReq)
	err := fws.Conn.WriteJSON(UpdateReq)

	if err != nil {
		log.Println("Error: ", err)
	}
	log.Println("Sent Update Order msg")
}

func (fws *FalconxWSClient) SendOrderCancelRequest(orderId string) {
	cancelReq := map[string]interface{}{
		"action":     "cancel_order_request",
		"request_id": "my_request_id_1234",
		"order_type": "limit",
		"order_details": map[string]interface{}{
			"client_order_id":      "unique_client_order_id25",
			"orig_client_order_id": "c3bebfc5a3e14285", // "unique_client_order_id321840
			"order_id":             orderId,            //mandatory
		},
	}
	err := fws.Conn.WriteJSON(cancelReq)

	if err != nil {
		log.Println("Error: ", err)
	}
	log.Println("Sent Update Order msg")
}

func (fws *FalconxWSClient) ReadMessages() {
	fws.readerActive = true
	defer func() { fws.readerActive = false }()
	// count := 0
	for {
		_, msg, errRead := safeRead(fws.Conn)
		if errRead != nil {
			fws.Conn.Close()
			return
		}

		var data ResponseMessage

		err := json.Unmarshal(msg, &data)
		switch data.Event {
		case "auth_response":
			{
				if err == nil && data.Status == "error" {
					log.Println("Authentication Failed. Err: ", data.Error, data.Body)
					fws.authenticated = false
				} else {
					log.Println("Authentication Successful", data)
					fws.authenticated = true
				}
				if len(fws.OrderIdFromResponse) == 0 {
					fws.responseChan <- fws.authenticated
				}
			}
		case "error_response":
			{
				log.Println("Error Response received. Err: ", data.Error, data.Body)
				fws.responseChan <- false
			}
		case "order_update":
			{
				log.Println("Order Update: ", data.Body)

				var orderResponse OrderResponse

				rawData, _ := json.Marshal(data.Body)

				err := json.Unmarshal(rawData, &orderResponse)

				if err != nil {
					log.Println("Error: ", err)
				}
				if len(fws.OrderIdFromResponse) == 0 {
					fws.OrderIdFromResponse <- orderResponse.OrderID
				}
			}
		case "order_response":
			{
				log.Println("Order Response: ", data.Body)

				var orderResponse OrderResponse
				rawData, _ := json.Marshal(data.Body)

				err := json.Unmarshal(rawData, &orderResponse)

				if err != nil {
					log.Println("Error: ", err)
				}
				if len(fws.OrderIdFromResponse) == 0 {
					fws.OrderIdFromResponse <- orderResponse.ClientOrderID
				}
			}
		case "create_order_ack":
			{
				log.Println("Create Order Ack: ", data.Body)

				var orderResponse OrderResponse
				rawData, _ := json.Marshal(data.Body)

				err := json.Unmarshal(rawData, &orderResponse)

				if err != nil {
					log.Println("Error: ", err)
				}
				if len(fws.OrderIdFromResponse) == 0 {
					fws.OrderIdFromResponse <- orderResponse.OrderID
				}
			}
		case "cancel_order_ack":
			{
				log.Println("Cancel Order Ack: ", data.Body)

				var orderResponse OrderResponse
				rawData, _ := json.Marshal(data.Body)

				err := json.Unmarshal(rawData, &orderResponse)

				if err != nil {
					log.Println("Error: ", err)
				}
				if len(fws.OrderIdFromResponse) == 0 {
					fws.OrderIdFromResponse <- orderResponse.OrderID
				}
			}
		case "update_order_ack":
			{
				log.Println("Update Order Ack: ", data.Body)

				var orderResponse OrderResponse
				rawData, _ := json.Marshal(data.Body)

				err := json.Unmarshal(rawData, &orderResponse)

				if err != nil {
					log.Println("Error: ", err)
				}
				if len(fws.OrderIdFromResponse) == 0 {
					fws.OrderIdFromResponse <- orderResponse.OrderID
				}
			}
		default:
			{
				log.Println("Unknown Event: ", data.Event, data.Body)
			}
		}
	}
}

func safeRead(conn *websocket.Conn) (messageType int, p []byte, err error) {
	messageType, r, err := conn.NextReader()
	if err == nil {
		p, err = ioutil.ReadAll(r)
	}
	return
}

func main() {
	waitChan := make(chan bool)
	log.SetFlags(log.LstdFlags | log.Lshortfile | log.Lmicroseconds)
	apiKey := "xxx"
	secret := "xxx"
	passphrase := "xxx"

	host := "order.falconx.io"

	path := "/order"

	fxClient := NewFalconxWSClient(host, path)
	fxClient.SetAuth(apiKey, secret, passphrase)
	fxClient.EnableSSL()
	fxClient.EnableRetry(1, nil)
	fxClient.Connect()
	fxClient.Authenticate()

	fxClient.SendOrderRequest()

	<-waitChan
}
