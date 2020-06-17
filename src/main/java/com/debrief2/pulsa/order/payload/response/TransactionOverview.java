package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionOverview {
  private long id;
  private String phoneNumber;
  private long price;
  private long voucher;
  private TransactionStatusName status;
  private Date createdAt;
}
