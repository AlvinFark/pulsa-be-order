package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.OtherServiceException;
import com.debrief2.pulsa.order.exception.ServiceUnreachableException;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.model.Voucher;
import com.debrief2.pulsa.order.payload.dto.VoucherDTO;
import org.springframework.http.HttpStatus;

public interface RPCService {

  boolean userExist(long id) throws OtherServiceException, ServiceUnreachableException;
  long getBalance(long id) throws OtherServiceException, ServiceUnreachableException;
  void decreaseBalance(long userId, long value) throws ServiceUnreachableException, OtherServiceException;
  void increaseBalance(long userId, long value);
  boolean eligibleToGetVoucher(long userId, long price, long providerId, long voucherId, long paymentMethodId) throws ServiceUnreachableException;
  VoucherDTO redeem(long userId, long voucherId, long price, long paymentMethodId, long providerId) throws OtherServiceException, ServiceUnreachableException;
  void unRedeem(long userId, long voucherId);
  void issue(long userId, long price, long providerId, long voucherId, long paymentMethodId);
  Voucher getVoucher(long id) throws ServiceUnreachableException;

  HttpStatus sendTopUpRequestTo3rdPartyServer(String phoneNumber, PulsaCatalog catalog);
}
