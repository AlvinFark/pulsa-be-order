package com.debrief2.pulsa.order.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionOverviewResponse {
  private long id;
  private String phone;
  private long price;
  private long voucher;
  private String status;
  private Date createdAt;
}
