package com.falconx.wsclient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import javax.crypto.spec.SecretKeySpec;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import javax.crypto.Mac;

import com.falconx.models.AuthRequest;
import com.falconx.models.DataRequest;
import com.falconx.models.Response;
import com.falconx.models.OrderRequest;
import com.falconx.models.OrderDetails;
import com.falconx.models.OrderFill;
import com.falconx.models.OrderResponse;
import com.falconx.models.SubscribeRequest;
import com.falconx.models.UnSubscribeRequest;
import com.falconx.wsclient.WebsocketClientConfigurator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;



@ClientEndpoint(configurator = WebsocketClientConfigurator.class)
public class FalconxWSOrderClient {

  private static CountDownLatch latch;

  // public void onConnect;
  static String host = "order.falconx.io";
  static String path = "/order";
  public String apiKey;
  public String secretKey;
  public String passphrase;

  public static String GetConnectURL(boolean ssl){
    return (ssl ? "wss://":"ws://" ) + FalconxWSOrderClient.host + FalconxWSOrderClient.path;
  }

  private String generateSignature() throws Exception {
    long timestamp = new Date().getTime() / 1000;

    String preHash = timestamp + "GET" + FalconxWSOrderClient.path;
    SecretKeySpec keyspec = new SecretKeySpec(Base64.getDecoder().decode(secretKey), "HmacSHA256");
    Mac sha256 = Mac.getInstance("HmacSHA256");
    sha256.init(keyspec);
    return Base64.getEncoder().encodeToString(sha256.doFinal(preHash.getBytes()));
  }

  public void Authenticate(Session session) {
    long timestamp = new Date().getTime() / 1000;

    try {
      AuthRequest authRequest = new AuthRequest(
          this.apiKey,
          this.passphrase,
          this.generateSignature(),
          timestamp,
          "my_request_id_1");

      ObjectMapper mapper = new ObjectMapper();
      session.getBasicRemote().sendText(mapper.writeValueAsString(authRequest));
    } catch (Exception ex) {
      System.out.println(ex);
    }
  }

  public String SendNewOrderRequest(Session session) {
    DateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ENGLISH);
    Date expiry;
    try {
      expiry = format.parse("2024-04-04 23:59:59");
    } catch (Exception e) {
      System.out.println("error"+ e);
      expiry = null;
    }
    OrderRequest request = new OrderRequest(
        "my_request_id_2",
        "limit",
        new OrderDetails(
            null,
            UUID.randomUUID().toString(),
            null,
            "ETH",
            "USD",
            0.01,
            "ETH",
            4000.0,
            "buy",
            "gtx",
            "2024-04-04T23:59:59Z"
        ),
        "create"
    );
    ObjectMapper mapper = new ObjectMapper();
    try {
      String requestString = mapper.writeValueAsString(request);
      System.out.println("Sending New Order Request -------------------------- ");
      System.out.println(requestString);
      return requestString;
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return "";
  }

  public String SendUpdateOrderRequest(Session session, String orderId, String origClientOrderId) {
    OrderRequest request = new OrderRequest(
        "my_request_id_2",
        "limit",
        new OrderDetails(
          orderId,
            "my_client_order_id",
            origClientOrderId,
            "ETH",
            "USD",
            0.01,
            "ETH",
            100.0,
            null,
            null,
            null
        ),
        "create"
    );
    ObjectMapper mapper = new ObjectMapper();
    try {
      String requestString = mapper.writeValueAsString(request);
      System.out.println("Sending Update Order Request -------------------------- ");
      System.out.println(requestString);
      return requestString;
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return "";
  }

  public String SendCancelOrderRequest(Session session, String orderId, String origClientOrderId) {
    OrderRequest request = new OrderRequest(
        "my_request_id_2",
        "limit",
        new OrderDetails(
          orderId,
            "my_client_order_id",
            origClientOrderId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ),
        "cancel"
    );
    ObjectMapper mapper = new ObjectMapper();
    try {
      String requestString = mapper.writeValueAsString(request);
      System.out.println("Sending Cancel Order Request -------------------------- ");
      System.out.println(requestString);
      return requestString;
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return "";
  }

  @OnOpen
  public void onOpen(Session session) {
    System.out.println("--- Connected " + session.getId());
    this.SetupConfig();
    this.Authenticate(session);
  }

  @OnMessage
  public String onMessage(String message, Session session) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      Response response = mapper.readValue(message, Response.class);
      switch (response.GetEvent()) {
        case "auth_response": {
          if (response.GetStatus().compareToIgnoreCase("success") == 0) {
            System.out.println("Auth Successful: " + message);

            return this.SendNewOrderRequest(session);
          } else {
            System.out.println("Authentication Failed: " + message);
          }
          break;
        }
        case "create_order_ack": {
          System.out.println("Received Create Order Acknowledgement -------------------------- ");
          System.out.println(message);
          break;
        }
        case "create_order_accepted": {
          System.out.println("Received Create Order Accepted -------------------------- ");
          System.out.println(message);
          break;
        }
        case "create_order_rejected": {
          System.out.println("Received Create Order Rejected -------------------------- ");
          System.out.println(message);
          break;
        }
        case "update_order_ack": {
          System.out.println("Received Update Order Acknowledgement -------------------------- ");
          System.out.println(message);
          break;
        }
        case "update_order_accepted": {
          System.out.println("Received Update Order Accepted -------------------------- ");
          System.out.println(message);
          break;
        }
        case "update_order_rejected": {
          System.out.println("Received Update Order Rejected -------------------------- ");
          System.out.println(message);
          break;
        }
        case "cancel_order_ack": {
          System.out.println("Received Cancel Order Acknowledgement -------------------------- ");
          System.out.println(message);
          break;
        }
        case "cancel_order_accepted": {
          System.out.println("Received Cancel Order Accepted -------------------------- ");
          System.out.println(message);
          break;
        }
        case "cancel_order_rejected": {
          System.out.println("Received Cancel Order Rejected -------------------------- ");
          System.out.println(message);
          break;
        }
        case "order_update": {
          System.out.println("Received Order Update -------------------------- ");
          System.out.println(message);
          break;
        }
        case "order_response": {
          System.out.println("Received Order Request Response -------------------------- ");
          System.out.println(message);
          break;
        }
        case "order_rejected": {
          System.out.println("Received Order Rejected -------------------------- ");
          System.out.println(message);
          break;
        }
        case "error_response": {
          System.out.println("Error Received: " + message);
          break;
        }
        default:{
          System.out.println("Unknown Event -> " + message);
          break;
        }
      }
      return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @OnClose
  public void onClose(Session session, CloseReason closeReason) {
    System.out.println("Session " + session.getId() +
        " closed because " + closeReason);
    latch.countDown();
  }

  public void SetupConfig() {
    this.apiKey = "xxx";
    this.secretKey = "xxx";
    this.passphrase = "xxx";
  }

  public static void main(String[] args) {
    latch = new CountDownLatch(1);
    ClientManager client = ClientManager.createClient();

    try {
      boolean enableSsl = true;
      URI uri = new URI(FalconxWSOrderClient.GetConnectURL(enableSsl));
      System.out.println("Connecting to " + uri);
      client.connectToServer(FalconxWSOrderClient.class, uri);
      latch.await();
    } catch (DeploymentException | URISyntaxException | InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
