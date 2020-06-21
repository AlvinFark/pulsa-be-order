package com.debrief2.pulsa.order.payload.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateTransactionRequest {
  private long userId;
  private String phoneNumber;
  private long catalogId;
}
