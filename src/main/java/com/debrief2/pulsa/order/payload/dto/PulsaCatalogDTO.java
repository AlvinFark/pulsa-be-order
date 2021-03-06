package com.debrief2.pulsa.order.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PulsaCatalogDTO {
  private long id;
  private long providerId;
  private long value;
  private long price;
  private Date deletedAt;
}
