package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnSubscribeRequest {
  public String action;

    @JsonProperty("base_token")
    public String baseToken;

    @JsonProperty("quote_token")
    public String quoteToken;

    @JsonProperty("symbol")
    public String symbol;

    @JsonProperty("request_id")
    public String requestId;

    @JsonProperty("tenor")
    public String tenor;

    public UnSubscribeRequest(String baseToken, String quoteToken, String symbol, String requestId, String tenor) {
      this.action = "unsubscribe";
      this.baseToken = baseToken;
      this.quoteToken = quoteToken;
      this.symbol = symbol;
      this.requestId = requestId;
      this.tenor = tenor;
    }
}
