package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.Provider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllPulsaCatalogResponse {
  private Provider provider;
  private List<PulsaCatalogResponse> catalog;
}
