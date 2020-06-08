package com.debrief2.pulsa.order.payload.dto;

import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusDTO {
  private long id;
  private TransactionStatusName name;
  private long typeId;
}
