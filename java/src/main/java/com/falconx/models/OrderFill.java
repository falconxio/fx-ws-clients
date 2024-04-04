package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class OrderFill {
  
  @JsonProperty("fx_quote_id")
  public String FxQuoteId;

  @JsonProperty("quantity")
  public TokenValue Quantity;

  @JsonProperty("price")
  public double Price;

  @JsonProperty("t_execute")
  public Date TExecute;

  public OrderFill(String fxQuoteId, TokenValue quantity, double price, Date tExecute) {
    this.FxQuoteId = fxQuoteId;
    this.Quantity = quantity;
    this.Price = price;
    this.TExecute = tExecute;
  }
}
