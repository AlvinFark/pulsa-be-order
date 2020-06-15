package com.debrief2.pulsa.order.model;

import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
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
public class Transaction {
  private long id;
  private PaymentMethodName method;
  private String phoneNumber;
  private PulsaCatalog catalog;
  private Voucher voucher;
  private TransactionStatusName status;
  private Date createdAt;
  private Date updatedAt;
}
