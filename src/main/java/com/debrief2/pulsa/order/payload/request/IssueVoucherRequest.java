package com.debrief2.pulsa.order.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueVoucherRequest {
  private long userId;
  private long price;
  private long providerId;
  private long voucherId;
  private long paymentMethodId;
}
