package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.payload.response.*;

import java.util.List;

public interface TransactionService {
  TransactionResponse getTransactionById(long id) throws ServiceException;
  TransactionResponse getTransactionByIdByUserId(long id, long UserId) throws ServiceException;
  Transaction updateTransaction(Transaction transaction);
  Transaction updateTransactionWithIssueVoucher(Transaction transaction);
  List<Transaction> getAllTransactionByUserIdAndStatusTypeAndPage(long userId, TransactionStatusType statusType, long page);
  List<RecentNumberResponse> getRecentNumber(long userId);
  Transaction createTransaction(long userId, long catalogId, String phone);
  Transaction pay(long userId, long transactionId, long methodId, long voucherId);
  TransactionResponseNoVoucher cancel(long userId, long transactionId) throws ServiceException;
  List<TransactionOverviewResponse> getHistoryInProgress(long userId, long page);
  List<TransactionOverviewResponse> getHistoryCompleted(long userId, long page);
  PaymentMethodName getPaymentMethodNameById(long id);
}
