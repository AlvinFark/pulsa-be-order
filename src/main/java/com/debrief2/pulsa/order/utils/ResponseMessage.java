package com.debrief2.pulsa.order.utils;

public class ResponseMessage {
  public static String orderGetAllCatalog400 = "invalid phone number";
  public static String orderGetAllCatalog404 = "unknown phone number";
  public static String cancelTransaction404 = "unknown transaction";
  public static String cancelTransaction400 = "can't cancel completed transaction";
}
