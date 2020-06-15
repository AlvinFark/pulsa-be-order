package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.Transaction;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.payload.response.*;

import java.util.List;

public interface TransactionService {
  Transaction getTransactionByIdByUserId(long id, long UserId) throws ServiceException;
  TransactionWithMethodId getTransactionById(long id) throws ServiceException;
  List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException;

  OrderResponse createTransaction(long userId, long catalogId, String phone) throws ServiceException;
  PayResponse pay(long userId, long transactionId, long methodId, long voucherId) throws ServiceException;
  TransactionNoVoucher cancel(long userId, long transactionId) throws ServiceException;

  List<TransactionOverview> getHistoryInProgress(long userId, long page) throws ServiceException;
  List<TransactionOverview> getHistoryCompleted(long userId, long page) throws ServiceException;

  TransactionStatusName getTransactionStatusNameById(long id);
  PaymentMethodName getPaymentMethodNameById(long id);
}
