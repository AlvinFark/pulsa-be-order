package com.debrief2.pulsa.order.utils;

public class ResponseMessage {
  public static String generic400 = "invalid request format";
  public static String getAllCatalog400 = "invalid phone number";
  public static String getAllCatalog404 = "unknown phone number";
  public static String cancelTransaction404 = "unknown transaction";
  public static String cancelTransaction400 = "can't cancel completed transaction";
  public static String getTransactionById404 = "unknown transaction";
  public static String getProviderById404 = "unknown provider";
  public static String getPaymentMethodNameById404 = "unknown method";
  public static String createTransaction409 = "you’ve already requested this exact order within the last 30 seconds, please try again later if you actually intended to do that";
  public static String createTransaction404catalog = "catalog not found";
  public static String createTransaction400Unauthorized = "selected catalog is not available for this phone’s provider";
  public static String createTransaction404phone = "unknown phone number";
  public static String createTransaction400phone = "invalid phone number";
  public static String member404 = "unknown user";

}
