package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.payload.response.*;

import java.util.List;

public interface TransactionService {
  TransactionResponse getTransactionByIdByUserId(long id, long UserId) throws ServiceException;
  TransactionResponseWithMethodId getTransactionById(long id) throws ServiceException;
  List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException;
  OrderResponse createTransaction(long userId, long catalogId, String phone) throws ServiceException;
  PayResponse pay(long userId, long transactionId, long methodId, long voucherId) throws ServiceException;
  TransactionResponseNoVoucher cancel(long userId, long transactionId) throws ServiceException;
  List<TransactionOverviewResponse> getHistoryInProgress(long userId, long page) throws ServiceException;
  List<TransactionOverviewResponse> getHistoryCompleted(long userId, long page) throws ServiceException;
  PaymentMethodName getPaymentMethodNameById(long id);

  void tmpDebugging();
}
