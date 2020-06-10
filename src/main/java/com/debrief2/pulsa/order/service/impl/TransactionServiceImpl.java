package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.utils.Global;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

  @Autowired
  TransactionMapper transactionMapper;
  @Autowired
  ProviderService providerService;

  @Override
  public TransactionResponse getTransactionById(long id) throws ServiceException {
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    if (transactionDTO==null){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }
    return transactionDTOtoTransactionResponseAdapter(transactionDTO);
  }

  @Override
  public TransactionResponse getTransactionByIdByUserId(long id, long userId) throws ServiceException {
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }
    return transactionDTOtoTransactionResponseAdapter(transactionDTO);
  }

  @Override
  public List<RecentNumberResponse> getRecentNumber(long userId) {
    List<TransactionDTO> transactionDTOS = transactionMapper.getTenRecentByUserId(userId);
    List<RecentNumberResponse> recentNumberResponses = new ArrayList<>();
    for (TransactionDTO transactionDTO:transactionDTOS) {
      Provider provider = providerService.getProviderByPrefix(transactionDTO.getPhoneNumber().substring(1,5));
      RecentNumberResponse recentNumberResponse = RecentNumberResponse.builder()
          .number(transactionDTO.getPhoneNumber())
          .provider(provider)
          .build();
      if (transactionDTO.getUpdatedAt()==null){
        recentNumberResponse.setDate(transactionDTO.getCreatedAt());
      } else {
        recentNumberResponse.setDate(transactionDTO.getUpdatedAt());
      }
      recentNumberResponses.add(recentNumberResponse);
    }
    return recentNumberResponses;
  }

  @Override
  public Transaction createTransaction(long userId, long catalogId, String phone) {
    return null;
  }

  @Override
  public Transaction pay(long userId, long transactionId, long methodId, long voucherId) {
    return null;
  }

  @Override
  public TransactionResponseNoVoucher cancel(long userId, long transactionId) throws ServiceException {
    TransactionDTO transactionDTO = transactionMapper.getById(transactionId);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.cancelTransaction404);
    }
    if (transactionDTO.getStatusId()!= getIdByTransactionStatusName(TransactionStatusName.WAITING)){
      throw new ServiceException(ResponseMessage.cancelTransaction400);
    }
    transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.CANCELED));
    transactionDTO.setVoucherId(0);
    transactionMapper.update(transactionDTO);
    TransactionDTO td = transactionMapper.getById(transactionId);
    return TransactionResponseNoVoucher.builder()
        .id(td.getId())
        .method(getPaymentMethodNameById(td.getMethodId()))
        .phoneNumber(td.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(td.getCatalogId())))
        .status(getTransactionStatusNameById(td.getStatusId()))
        .createdAt(td.getCreatedAt())
        .updatedAt(td.getUpdatedAt())
        .build();
  }

  @Override
  public List<TransactionOverviewResponse> getHistoryInProgress(long userId, long page) {
    return getHistory(userId,page,TransactionStatusType.IN_PROGRESS);
  }

  @Override
  public List<TransactionOverviewResponse> getHistoryCompleted(long userId, long page) {
    return getHistory(userId,page,TransactionStatusType.COMPLETED);
  }

  private List<TransactionOverviewResponse> getHistory(long userId, long page, TransactionStatusType transactionStatusType){
    transactionMapper.refreshStatus(userId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    long offset = (page-1)*10;
    List<TransactionDTO> transactionDTOS = transactionMapper.getAllByUserIdAndStatusTypeIdAndOffset(userId,getIdByTransactionStatusType(transactionStatusType),offset);
    List<TransactionOverviewResponse> transactionOverviewResponses = new ArrayList<>();
    for (TransactionDTO transactionDTO:transactionDTOS) {
      transactionOverviewResponses.add(transactionDTOtoTransactionOverviewResponseAdapter(transactionDTO));
    }
    return transactionOverviewResponses;
  }

  @Override
  public PaymentMethodName getPaymentMethodNameById(long id){
    try {
      return PaymentMethodName.values()[(int) id - 1];
    } catch (ArrayIndexOutOfBoundsException e) {
      return null;
    }
  }

  private long getIdByPaymentMethodName(PaymentMethodName paymentMethodName){
    return paymentMethodName.ordinal()+1;
  }

  private TransactionStatusName getTransactionStatusNameById(long id){
    try {
      return TransactionStatusName.values()[(int) id-1];
    } catch (ArrayIndexOutOfBoundsException e) {
      return null;
    }
  }

  private long getIdByTransactionStatusName(TransactionStatusName transactionStatusName){
    return transactionStatusName.ordinal()+1;
  }

  private TransactionStatusType getTransactionStatusTypeById(long id){
    try {
      return TransactionStatusType.values()[(int) id-1];
    } catch (ArrayIndexOutOfBoundsException e) {
      return null;
    }
  }

  private long getIdByTransactionStatusType(TransactionStatusType transactionStatusType){
    return transactionStatusType.ordinal()+1;
  }

  private TransactionResponse transactionDTOtoTransactionResponseAdapter(TransactionDTO transactionDTO){
//    Voucher voucher = get from promotion domain (transactionDTO.getVoucherId)
    //calculate deduction
    return TransactionResponse.builder()
        .id(transactionDTO.getId())
        .method(getPaymentMethodNameById(transactionDTO.getMethodId()))
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId())))
//        .voucher(voucher)
        .status(getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .updatedAt(transactionDTO.getUpdatedAt())
        .build();
  }

  private TransactionOverviewResponse transactionResponseToTransactionOverviewResponseAdapter(TransactionResponse transactionResponse){
    long voucher = 0;
    if (transactionResponse.getVoucher()!=null){
      voucher = transactionResponse.getVoucher().getDeduction();
    }
    return TransactionOverviewResponse.builder()
        .id(transactionResponse.getId())
        .phoneNumber(transactionResponse.getPhoneNumber())
        .price(transactionResponse.getCatalog().getPrice())
        .voucher(voucher)
        .status(transactionResponse.getStatus().name())
        .createdAt(transactionResponse.getCreatedAt())
        .build();
  }

  private TransactionOverviewResponse transactionDTOtoTransactionOverviewResponseAdapter(TransactionDTO transactionDTO){
    return transactionResponseToTransactionOverviewResponseAdapter(transactionDTOtoTransactionResponseAdapter(transactionDTO));
  }
}