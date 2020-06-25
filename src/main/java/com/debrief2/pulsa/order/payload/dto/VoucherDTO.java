package com.debrief2.pulsa.order.payload.dto;

import com.debrief2.pulsa.order.model.enums.VoucherType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDTO {
  private long id;
  private String name;
  private long deduction;
  private long maxDeduction;
  private long discount;
  private long minPurchase;
  private VoucherType voucherTypeName;
  private String filePath;
  private boolean active;
  private long finalPrice;
  private long value;
  private Date expiryDate;
}
