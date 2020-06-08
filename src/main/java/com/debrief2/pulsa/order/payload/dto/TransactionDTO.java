package com.debrief2.pulsa.order.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
  private long id;
  private long userId;
  private long methodId;
  private String phoneNumber;
  private long catalogId;
  private long voucherId;
  private long statusId;
  private Date createdAt;
  private Date updatedAt;
}
