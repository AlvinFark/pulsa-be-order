package com.debrief2.pulsa.order.payload.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRequest {
  private long userId;
  private long transactionId;
  private long methodId;
  private long voucherId;
}
