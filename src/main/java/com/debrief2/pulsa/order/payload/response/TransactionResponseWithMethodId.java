package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.model.Voucher;
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
public class TransactionResponseWithMethodId {
  private long id;
  private long methodId;
  private String phoneNumber;
  private PulsaCatalog catalog;
  private Voucher voucher;
  private TransactionStatusName status;
  private Date createdAt;
  private Date updatedAt;
}
