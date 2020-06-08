package com.debrief2.pulsa.order.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleResponseWrapper {
  private int code;
  private String message;
}
