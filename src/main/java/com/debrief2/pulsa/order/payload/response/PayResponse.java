package com.debrief2.pulsa.order.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayResponse {
  private long balance;
  private boolean rewardVoucher;
  private TransactionResponse transaction;
}
