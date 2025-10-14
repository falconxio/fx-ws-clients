import websocket
import json
import hmac
import hashlib
import base64
import time
import threading
import ssl
import logging
from datetime import datetime, timedelta, timezone
import string
import random
from typing import Optional, Dict, Any

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s.%(msecs)03d %(levelname)s %(module)s - %(funcName)s: %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

class TokenPair:
    def __init__(self, base_token: str, quote_token: str):
        self.base_token = base_token
        self.quote_token = quote_token
    
    def __repr__(self):
        return f"{self.base_token}/{self.quote_token}"

class TokenValue:
    def __init__(self, token: str, value: float):
        self.token = token
        self.value = value

class OrderFill:
    def __init__(self, fx_quote_id: str, quantity: TokenValue, price: float, t_execute: datetime):
        self.fx_quote_id = fx_quote_id
        self.quantity = quantity
        self.price = price
        self.t_execute = t_execute

class OrderResponse:
    def __init__(self, client_order_id: str, order_id: str, order_status: str,
                 token_pair: TokenPair, quantity: TokenValue, limit_price: Optional[float] = None,
                 executed_price: Optional[float] = None, side: Optional[str] = None,
                 order_type: Optional[str] = None, time_in_force: Optional[str] = None,
                 expiry_time: Optional[datetime] = None, fills: Optional[list] = None,
                 error: Any = None):
        self.client_order_id = client_order_id
        self.order_id = order_id
        self.order_status = order_status
        self.token_pair = token_pair
        self.quantity = quantity
        self.limit_price = limit_price
        self.executed_price = executed_price
        self.side = side
        self.order_type = order_type
        self.time_in_force = time_in_force
        self.expiry_time = expiry_time
        self.fills = fills or []
        self.error = error

def generate_id(length: int) -> str:
    """Generate a random ID of specified length."""
    chars = string.ascii_letters + string.digits
    return ''.join(random.choice(chars) for _ in range(length))

def get_sign(timestamp: int, secret_key: str, path: str) -> str:
    """Generate signed message for authentication."""
    message = f"{timestamp}GET{path}"
    key = base64.b64decode(secret_key)
    signature = hmac.new(key, message.encode(), hashlib.sha256)
    return base64.b64encode(signature.digest()).decode()

class FalconxWSClient:
    def __init__(self, host: str, path: str):
        self.host = host
        self.path = path
        self.ssl_enabled = False
        self.api_key = ""
        self.secret = ""
        self.passphrase = ""
        self.ws = None
        self.retry_on_error = False
        self.num_retries = None
        self.retry_delay = 1
        self.retry_count = 0
        self.authenticated = False
        self.log_streams = True
        self.reader_active = False
        self.response_event = threading.Event()
        self.order_id_queue = []
        
    def enable_ssl(self):
        """Enable SSL for secure connection."""
        self.ssl_enabled = True
        
    def set_auth(self, api_key: str, secret: str, passphrase: str):
        """Set authentication credentials."""
        self.api_key = api_key
        self.secret = secret
        self.passphrase = passphrase
        
    def enable_retry(self, retry_delay_seconds: int, num_retries: Optional[int]):
        """Enable retry mechanism with specified delay and number of retries."""
        self.retry_on_error = True
        self.retry_delay = max(1, retry_delay_seconds)
        self.num_retries = num_retries
        
    def on_message(self, ws, message):
        """Handle incoming WebSocket messages."""
        data = json.loads(message)
        event = data.get('event')
        
        if event == 'auth_response':
            if data.get('status') == 'error':
                logging.error(f"Authentication Failed. Error: {data.get('error')}")
                self.authenticated = False
            else:
                logging.info("Authentication Successful")
                self.authenticated = True
            self.response_event.set()
            
        elif event == 'error_response':
            logging.error(f"Error Response received. Error: {data.get('error')}")
            self.response_event.set()
            
        elif event in ['create_order_ack', 'create_order_accepted', 'create_order_rejected',
                    'update_order_ack', 'update_order_accepted', 'update_order_rejected',
                    'cancel_order_ack', 'cancel_order_accepted', 'cancel_order_rejected',
                    'order_update', 'order_response']:
            logging.info(f"{event}: {data.get('body')}")
            body = data.get('body', {})
            order_id = body.get('order_id') or body.get('client_order_id')
            if order_id:
                self.order_id_queue.append(order_id)
                
        else:
            logging.info(f"Unknown Event: {event}")
            
    def on_error(self, ws, error):
        """Handle WebSocket errors."""
        logging.error(f"WebSocket error: {error}")
        
    def on_close(self, ws, close_status_code, close_msg):
        """Handle WebSocket connection closure."""
        logging.info("WebSocket connection closed")
        
    def on_open(self, ws):
        """Handle WebSocket connection opening."""
        logging.info("WebSocket connection opened")
        
    def connect(self):
        """Establish WebSocket connection."""
        protocol = "wss" if self.ssl_enabled else "ws"
        url = f"{protocol}://{self.host}{self.path}"
        
        # websocket.enableTrace(self.log_streams)
        self.ws = websocket.WebSocketApp(
            url,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close,
            on_open=self.on_open
        )
        
        if self.ssl_enabled:
            self.ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})
        else:
            self.ws.run_forever()
            
    def authenticate(self) -> bool:
        """Authenticate with the WebSocket server."""
        timestamp = int(time.time())
        signature = get_sign(timestamp, self.secret, self.path)
        
        auth_request = {
            "action": "auth",
            "api_key": self.api_key,
            "passphrase": self.passphrase,
            "signature": signature,
            "timestamp": timestamp,
            "request_id": "auth_request"
        }
        
        self.ws.send(json.dumps(auth_request))
        self.response_event.wait(timeout=5)
        return self.authenticated
        
    def send_order_request(self):
        """Send a new order request."""
        client_order_id = generate_id(32)
        request = {
            "action": "create_order_request",
            "request_id": "order_request_1234",
            "order_type": "limit",
            "order_details": {
                "client_order_id": client_order_id,
                "base_token": "ETH",
                "quote_token": "USD",
                "quantity": 0.01,
                "quantity_token": "ETH",
                "limit_price": 3000,
                "side": "sell",
                "tif": "gtx",
                "expiry": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
            }
        }
        
        self.ws.send(json.dumps(request))
        logging.info("Sent Create Order message")
        logging.info(f"Create Request: {request}")
        
    def send_order_update_request(self, order_id: str, client_order_id: str):
        """Send an order update request."""
        new_client_order_id = generate_id(16)
        request = {
            "action": "update_order_request",
            "request_id": "update_request_1234",
            "order_type": "limit",
            "order_details": {
                "orig_client_order_id": client_order_id,
                "client_order_id": new_client_order_id,
                "order_id": order_id,
                "base_token": "ETH",
                "quote_token": "USD",
                "quantity": 0.0004,
                "quantity_token": "ETH",
                "limit_price": 80001,
                "side": "sell"
            }
        }
        
        self.ws.send(json.dumps(request))
        logging.info("Sent Update Order message")
        logging.info(f"Update Request: {request}")
        
    def send_order_cancel_request(self, order_id: str):
        """Send an order cancellation request."""
        request = {
            "action": "cancel_order_request",
            "request_id": "cancel_request_1234",
            "order_type": "limit",
            "order_details": {
                "client_order_id": "unique_client_order_id25",
                "orig_client_order_id": "c3bebfc5a3e14285",
                "order_id": order_id
            }
        }
        
        self.ws.send(json.dumps(request))
        logging.info("Sent Cancel Order message")
        logging.info(f"Cancel Request: {request}")

def main():
    # Example usage
    api_key = "xxx"
    passphrase = "xxx"
    secret = "xxx"
    host = "order.falconx.io"
    path = "/order"
    
    client = FalconxWSClient(host, path)
    client.set_auth(api_key, secret, passphrase)
    client.enable_ssl()
    client.enable_retry(1, None)
    
    # Start WebSocket connection in a separate thread
    ws_thread = threading.Thread(target=client.connect)
    ws_thread.daemon = True
    ws_thread.start()
    
    # Wait for connection to establish
    time.sleep(2)
    
    # Authenticate and send order
    if client.authenticate():
        client.send_order_request()
    
    # Keep main thread running
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info("Shutting down...")

if __name__ == "__main__":
    main()
