package com.debrief2.pulsa.order.utils;

public class ResponseMessage {
  public static String generic400 = "invalid request format";
  public static String getAllCatalog400 = "invalid phone number";
  public static String getAllCatalog404 = "unknown phone number";
  public static String cancelTransaction404 = "unknown transaction";
  public static String cancelTransaction400 = "can't cancel completed transaction";
  public static String getRecentNumber400 = "invalid phone number";
  public static String getProviderById400 = "invalid id format";
  public static String getPaymentMethodNameById400 = "invalid id format";

}
