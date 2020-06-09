package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.payload.dto.ProviderPrefixDTO;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.repository.ProviderMapper;
import com.debrief2.pulsa.order.repository.PulsaCatalogMapper;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import com.debrief2.pulsa.order.service.AsyncAdapter;
import com.debrief2.pulsa.order.service.OrderService;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderServiceImpl implements OrderService {

  @Autowired
  TransactionMapper transactionMapper;
  @Autowired
  ProviderService providerService;

  @Override
  public AllPulsaCatalogResponse getAllCatalog(String phone) throws ServiceException {
    //validate format
    if (phone.length()!=5||phone.charAt(0)!='0'){
      throw new ServiceException(ResponseMessage.orderGetAllCatalog400);
    }

    //validate if phone number prefix exist
    Provider provider = providerService.getProviderByPrefix(phone.substring(1));
    if (provider==null||provider.getDeletedAt()!=null){
      throw new ServiceException(ResponseMessage.orderGetAllCatalog404);
    }

    return new AllPulsaCatalogResponse(provider,providerService.getCatalogResponseByProviderId(provider.getId()));
  }

  @Override
  public Transaction getTransactionById(long id) {
    return null;
  }

  @Override
  public Transaction updateTransaction(Transaction transaction) {
    return null;
  }

  @Override
  public Transaction updateTransactionWithIssueVoucher(Transaction transaction) {
    return null;
  }

  @Override
  public List<Transaction> getAllTransactionByUserIdAndStatusTypeAndPage(long userId, TransactionStatusType statusType, long page) {
    return null;
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
    if (transactionDTO.getStatusId()!= TransactionStatusName.WAITING.ordinal()+1){
      throw new ServiceException(ResponseMessage.cancelTransaction400);
    }
    transactionDTO.setStatusId(TransactionStatusName.CANCELED.ordinal()+1);
    transactionDTO.setVoucherId(0);
    transactionMapper.update(transactionDTO);
    TransactionDTO td = transactionMapper.getById(transactionId);
    return TransactionResponseNoVoucher.builder()
        .id(td.getId())
        .method(PaymentMethodName.values()[(int) td.getMethodId()-1])
        .phoneNumber(td.getPhoneNumber())
        .catalog(catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(td.getCatalogId())))
        //.voucher()
        .status(TransactionStatusName.values()[(int) td.getStatusId()-1])
        .createdAt(td.getCreatedAt())
        .updatedAt(td.getUpdatedAt())
        .build();
  }

  @Override
  public List<TransactionOverviewResponse> getHistoryInProgress(long userId, long page) {
    return null;
  }

  @Override
  public List<TransactionOverviewResponse> getHistoryCompleted(long userId, long page) {
    return null;
  }

  public PulsaCatalog catalogDTOToCatalogAdapter(PulsaCatalogDTO catalogDTO){
    return PulsaCatalog.builder()
        .id(catalogDTO.getId())
        .provider(providerService.getProviderById(catalogDTO.getProviderId()))
        .value(catalogDTO.getValue())
        .price(catalogDTO.getPrice())
        .deletedAt(catalogDTO.getDeletedAt())
        .build();
  };
}
