package com.debrief2.pulsa.order.model;

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
//  private User user;
  private PaymentMethod method;
  private String phoneNumber;
  private PulsaCatalog catalog;
//  private Voucher voucher;
  private TransactionStatus status;
  private Date createdAt;
  private Date updatedAt;
}
