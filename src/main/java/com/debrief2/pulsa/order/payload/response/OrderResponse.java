package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.PulsaCatalog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
  private long id;
  private String phoneNumber;
  private PulsaCatalog catalog;
}
