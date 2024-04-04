package com.falconx.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderRequest {
  
  @JsonProperty("action")
  public String action;

  @JsonProperty("request_id")
  public String requestId;

  @JsonProperty("order_type")
  public String orderType;

  @JsonProperty("order_details")
  public OrderDetails orderDetails;

  public OrderRequest(String requestId, String orderType, OrderDetails orderDetails, String action) {
    switch (action.toLowerCase()) {
      case "create":
        this.action = "create_order_request";
        break;
      case "cancel":
        this.action = "cancel_order_request";
        break;
      case "update":
        this.action = "update_order_request";
        break;
      default:
        throw new IllegalArgumentException("Invalid action: " + action);
    }
    this.action = "create_order_request";
    this.requestId = requestId;
    this.orderType = orderType;
    this.orderDetails = orderDetails;
  }
}
