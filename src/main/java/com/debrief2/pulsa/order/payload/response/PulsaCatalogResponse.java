package com.debrief2.pulsa.order.payload.response;

import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PulsaCatalogResponse {
  private long id;
  private long value;
  private long price;

  public PulsaCatalogResponse(PulsaCatalog pulsaCatalog){
    this.id = pulsaCatalog.getId();
    this.price = pulsaCatalog.getPrice();
    this.value = pulsaCatalog.getValue();
  }

  public PulsaCatalogResponse(PulsaCatalogDTO pulsaCatalogDTO){
    this.id = pulsaCatalogDTO.getId();
    this.price = pulsaCatalogDTO.getPrice();
    this.value = pulsaCatalogDTO.getValue();
  }
}
