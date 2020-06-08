package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.Provider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentNumberResponse {
  private String number;
  private Provider provider;
  private Date date;
}
