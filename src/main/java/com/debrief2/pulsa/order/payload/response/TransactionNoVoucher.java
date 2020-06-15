package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.PulsaCatalog;
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
public class TransactionNoVoucher {
  private long id;
  private PaymentMethodName method;
  private String phoneNumber;
  private PulsaCatalog catalog;
  private TransactionStatusName status;
  private Date createdAt;
  private Date updatedAt;
}
