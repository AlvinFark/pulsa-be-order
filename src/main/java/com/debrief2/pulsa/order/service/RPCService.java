package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.OtherServiceException;
import com.debrief2.pulsa.order.exception.ServiceUnreachableException;
import com.debrief2.pulsa.order.model.Voucher;

public interface RPCService {

  boolean userExist(long id) throws ServiceUnreachableException, OtherServiceException;
  long getBalance(long id) throws ServiceUnreachableException, OtherServiceException;
  void decreaseBalance(long userId, long value) throws ServiceUnreachableException, OtherServiceException;
  void increaseBalance(long userId, long value) throws ServiceUnreachableException, OtherServiceException;
  boolean eligibleToGetVoucher(long userId, long price, long providerId, long voucherId, long paymentMethodId) throws ServiceUnreachableException, OtherServiceException;
  Voucher redeem(long userId, long voucherId, long price, long paymentMethodId, long providerId) throws OtherServiceException, ServiceUnreachableException;
  void unRedeem(long userId, long voucherId) throws ServiceUnreachableException, OtherServiceException;
  void issue(long userId, long price, long providerId, long voucherId, long paymentMethodId) throws ServiceUnreachableException, OtherServiceException;
  Voucher getVoucher(long id) throws ServiceUnreachableException, OtherServiceException;
}
