package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenValue {
    
    @JsonProperty("token")
    public String Token;
  
    @JsonProperty("value")
    public double Value;
  
    public TokenValue(String token, double value) {
      this.Token = token;
      this.Value = value;
    }
}
