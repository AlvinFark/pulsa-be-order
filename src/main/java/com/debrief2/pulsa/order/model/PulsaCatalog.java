package com.debrief2.pulsa.order.model;

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
public class PulsaCatalog {
  private long id;
  private Provider provider;
  private long value;
  private long price;
  @JsonIgnore
  private Date deletedAt;
}
