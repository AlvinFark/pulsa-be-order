package com.debrief2.pulsa.order.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseWrapper extends SimpleResponseWrapper {
  Object data;
  public ResponseWrapper(int code, String message, Object data){
    super(code,message);
    this.data = data;
  }
}
