package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenPair {
  
  @JsonProperty("base_token")
  public String BaseToken;

  @JsonProperty("quote_token")
  public String QuoteToken;

  public TokenPair(String baseToken, String quoteToken) {
    this.BaseToken = baseToken;
    this.QuoteToken = quoteToken;
  }
}
