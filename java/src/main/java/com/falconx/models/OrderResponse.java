package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Date;


public class OrderResponse {
  
  @JsonProperty("client_order_id")
  public String ClientOrderId;

  @JsonProperty("order_id")
  public String OrderId;

  @JsonProperty("order_status")
  public String OrderStatus;

  @JsonProperty("token_pair")
  public TokenPair TokenPair;

  @JsonProperty("quantity")
  public TokenValue Quantity;

  @JsonProperty("limit_price")
  public double LimitPrice;

  @JsonProperty("executed_price")
  public double ExecutedPrice;

  @JsonProperty("side")
  public String Side;

  @JsonProperty("order_type")
  public String OrderType;

  @JsonProperty("time_in_force")
  public String TimeInForce;

  @JsonProperty("expiry_time")
  public Date ExpiryTime;

  @JsonProperty("fills")
  public List<OrderFill> Fills;

  @JsonProperty("error")
  public Object Error;

  public OrderResponse(String clientOrderId, String orderId, String orderStatus, TokenPair tokenPair, TokenValue quantity, double limitPrice, double executedPrice, String side, String orderType, String timeInForce, Date expiryTime, List<OrderFill> fills, Object error) {
    this.ClientOrderId = clientOrderId;
    this.OrderId = orderId;
    this.OrderStatus = orderStatus;
    this.TokenPair = tokenPair;
    this.Quantity = quantity;
    this.LimitPrice = limitPrice;
    this.ExecutedPrice = executedPrice;
    this.Side = side;
    this.OrderType = orderType;
    this.TimeInForce = timeInForce;
    this.ExpiryTime = expiryTime;
    this.Fills = fills;
    this.Error = error;
  }
}
