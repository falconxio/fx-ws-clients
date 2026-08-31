package com.falconx.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscribeRequest {
  public String action;

    @JsonProperty("base_token")
    public String baseToken;

    @JsonProperty("quote_token")
    public String quoteToken;

    @JsonProperty("symbol")
    public String symbol;

    public Quantity quantity;

    @JsonProperty("request_id")
    public String requestId;

    @JsonProperty("tenor")
    public String tenor;

    public SubscribeRequest(String baseToken, String quoteToken, String symbol, String quantityToken,
        List<Double> levels, String requestId, String tenor) {
      this.action = "subscribe";
      this.baseToken = baseToken;
      this.quoteToken = quoteToken;
      this.symbol = symbol;
      this.quantity = new Quantity(quantityToken, levels);
      this.requestId = requestId;
      this.tenor = tenor;
    }
}
