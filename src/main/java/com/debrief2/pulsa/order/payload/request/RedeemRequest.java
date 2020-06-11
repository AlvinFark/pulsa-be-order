package com.debrief2.pulsa.order.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemRequest {
  private long userId;
  private long voucherId;
  private long price;
  private long paymentMethodId;
  private long providerId;
}
