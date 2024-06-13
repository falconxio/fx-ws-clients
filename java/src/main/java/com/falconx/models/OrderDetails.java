package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Date;


public class OrderDetails {

  @JsonProperty("order_id")
  public String OrderId;

  @JsonProperty("client_order_id")
  public String ClientOrderId;
  
  @JsonProperty("orig_client_order_id")
  public String OrigClientOrderId;

  @JsonProperty("base_token")
  public String BaseToken;

  @JsonProperty("quote_token")
  public String QuoteToken;

  @JsonProperty("quantity")
  public Double Quantity;

  @JsonProperty("quantity_token")
  public String QuantityToken;

  @JsonProperty("limit_price")
  public Double LimitPrice;

  @JsonProperty("side")
  public String Side;

  @JsonProperty("tif")
  public String Tif;

  @JsonProperty("expiry")
  public String Expiry;

  public OrderDetails(String order_id, String clientOrderId, String origClientOrderId, String baseToken, String quoteToken, Double quantity, String quantityToken, Double limitPrice, String side, String tif, String expiry) {
    this.OrderId = order_id;
    this.ClientOrderId = clientOrderId;
    this.OrigClientOrderId = origClientOrderId;
    this.BaseToken = baseToken;
    this.QuoteToken = quoteToken;
    this.Quantity = quantity;
    this.QuantityToken = quantityToken;
    this.LimitPrice = limitPrice;
    this.Side = side;
    this.Tif = tif;
    this.Expiry = expiry;
  }
}

