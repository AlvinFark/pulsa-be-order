package com.debrief2.pulsa.order.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentNumberDTO {
  private String number;
  private long providerId;
  private long providerName;
  private long providerImage;
  private Date date;
}
