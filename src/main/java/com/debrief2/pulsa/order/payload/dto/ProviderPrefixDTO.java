package com.debrief2.pulsa.order.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPrefixDTO {
  private long id;
  private long providerId;
  private String prefix;
}
