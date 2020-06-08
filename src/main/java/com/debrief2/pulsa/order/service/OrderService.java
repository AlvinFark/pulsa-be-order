package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.payload.response.*;

import java.util.List;

public interface OrderService {
  Provider getProviderByPhone(String phone);
  PulsaCatalog getCatalogById(long id);
  Provider getProviderById(long id);
  Transaction getTransactionDetailedById(long id);
  Transaction getTransactionById(long id);
  PaymentMethod getMethodById(long id);
  Transaction updateTransaction(Transaction transaction);
  Transaction updateTransactionWithIssueVoucher(Transaction transaction);
  List<Transaction> getAllTransactionByUserIdAndStatusTypeAndPage(long userId, TransactionStatusType statusType, long page);
  AllPulsaCatalogResponse getAllCatalog(String phone) throws ServiceException;
  List<RecentNumberResponse> getRecentNumber(long userId);
  Transaction createTransaction(long userId, long catalogId, String phone);
  Transaction pay(long userId, long transactionId, long methodId, long voucherId);
  TransactionResponseNoVoucher cancel(long userId, long transactionId) throws ServiceException;
  List<TransactionOverviewResponse> getHistoryInProgress(long userId, long page);
  List<TransactionOverviewResponse> getHistoryCompleted(long userId, long page);
  void checkAllCache();
  void reloadPrefix();
  void reloadCatalog();
}
