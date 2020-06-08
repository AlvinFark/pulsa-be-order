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
public class Provider {
  private long id;
  private String name;
  private String image;
  @JsonIgnore
  private Date deletedAt;
}
