package com.debrief2.pulsa.order.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnRedeemRequest {
  private long userId;
  private long voucherId;
}
