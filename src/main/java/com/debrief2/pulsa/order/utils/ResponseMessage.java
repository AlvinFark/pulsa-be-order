package com.debrief2.pulsa.order.utils;

public class ResponseMessage {
  public static final String transaction404 = "unknown transaction";
  public static final String phone400 = "invalid phone number";
  public static final String phone404 = "unknown phone number";
  public static final String catalog404 = "catalog not found";
  public static final String catalog400 = "selected catalog is not available for this phone’s provider";
  public static final String method404 = "unknown method";
  public static final String cancelTransaction400 = "can't cancel completed transaction";
  public static final String createTransaction409 = "you’ve already requested this exact order within the last 30 seconds, please try again later if you actually intended to do that";
  public static final String provider404 = "unknown provider";

  /////
  public static final String generic400 = "invalid request format";
  public static final String getAllCatalog400 = "invalid phone number";
  public static final String getAllCatalog404 = "unknown phone number";
  public static final String member404 = "unknown user";
  public static final String pay404transaction = "unknown transaction";
  public static final String pay400 = "not enough balance";
  public static final String memberConnection = "member service unreachable";
  public static final String promotionConnection = "promotion service unreachable";
}
