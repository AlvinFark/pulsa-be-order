package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.payload.dto.ProviderPrefixDTO;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.response.PulsaCatalogResponse;
import com.debrief2.pulsa.order.payload.response.RecentNumberResponse;
import com.debrief2.pulsa.order.payload.response.TransactionOverviewResponse;
import com.debrief2.pulsa.order.payload.response.TransactionResponse;
import com.debrief2.pulsa.order.repository.ProviderMapper;
import com.debrief2.pulsa.order.repository.PulsaCatalogMapper;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import com.debrief2.pulsa.order.service.AsyncAdapter;
import com.debrief2.pulsa.order.service.OrderService;
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
  ProviderMapper providerMapper;
  @Autowired
  PulsaCatalogMapper pulsaCatalogMapper;
  @Autowired
  TransactionMapper transactionMapper;
  @Autowired
  AsyncAdapter asyncAdapter;

  private static HashMap<Long, Provider> mapProviderById = new HashMap<>();
  private static HashMap<String, Long> mapProviderIdByPrefix = new HashMap<>();
  private static HashMap<Long, PulsaCatalogDTO> mapCatalogDTOById = new HashMap<>();
  private static HashMap<Long, ArrayList<PulsaCatalogResponse>> mapListCatalogResponseByProviderId = new HashMap<>();

  @Override
  public Provider getProviderByPhone(String phone){
    return null;
  }

  @Override
  public PulsaCatalog getCatalogById(long id) {
    return null;
  }

  @Override
  public List<PulsaCatalogResponse> getAllCatalog(String phone) throws ServiceException {
    //validate format
    if (phone.length()!=5||phone.charAt(0)!='0'){
      throw new ServiceException(ResponseMessage.orderGetAllCatalog400);
    }

    //validate if phone number prefix exist
    checkAllCache();

    try {
      long providerId = mapProviderIdByPrefix.get(phone.substring(1));
      Provider provider = mapProviderById.get(providerId);
      if (provider==null||provider.getDeletedAt()!=null){
        throw new ServiceException(ResponseMessage.orderGetAllCatalog404);
      }
      return mapListCatalogResponseByProviderId.get(providerId);
    } catch (NullPointerException e){
      throw new ServiceException(ResponseMessage.orderGetAllCatalog404);
    }
  }

  @Override
  public Provider getProviderById(long id) {
    return null;
  }

  @Override
  public Transaction getTransactionDetailedById(long id) {
    return null;
  }

  @Override
  public Transaction getTransactionById(long id) {
    return null;
  }

  @Override
  public PaymentMethod getMethodById(long id) {
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
    checkAllCache();
    System.out.println(transactionDTOS.toString());
    for (TransactionDTO transactionDTO:transactionDTOS) {
      long providerId = mapProviderIdByPrefix.get(transactionDTO.getPhoneNumber().substring(1,5));
      Provider provider = mapProviderById.get(providerId);
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
  public TransactionResponse cancel(long userId, long transactionId) throws ServiceException {
    TransactionDTO transactionDTO = transactionMapper.getById(transactionId);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.cancelTransaction404);
    }
    if (transactionDTO.getStatusId()!= TransactionStatusName.WAITING.ordinal()+1){
      throw new ServiceException(ResponseMessage.cancelTransaction400);
    }
    transactionDTO.setStatusId(TransactionStatusName.CANCELED.ordinal()+1);
    transactionMapper.update(transactionDTO);
    TransactionDTO td = transactionMapper.getById(transactionId);
    checkAllCache();
    return TransactionResponse.builder()
        .id(td.getId())
        .method(PaymentMethodName.values()[(int) td.getMethodId()-1])
        .phoneNumber(td.getPhoneNumber())
        .catalog(catalogDTOToCatalogAdapter(mapCatalogDTOById.get(td.getCatalogId())))
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

  @Override
  public void reloadCatalog(){
    //get from db
    List<PulsaCatalogDTO> pulsaCatalogDTOS = pulsaCatalogMapper.getAll();
    //put it in hash map
    mapCatalogDTOById = new HashMap<>();
    mapListCatalogResponseByProviderId = new HashMap<>();
    for (PulsaCatalogDTO pulsaCatalogDTO:pulsaCatalogDTOS) {
      mapCatalogDTOById.put(pulsaCatalogDTO.getId(),pulsaCatalogDTO);
      if (pulsaCatalogDTO.getDeletedAt()==null) {
        mapListCatalogResponseByProviderId.computeIfAbsent(pulsaCatalogDTO.getProviderId(), k -> new ArrayList<>()).add(new PulsaCatalogResponse(pulsaCatalogDTO));
      }
    }
  }

  public void reloadProvider(){
    //get from db
    List<Provider> providers = providerMapper.getAll();
    //convert provider to map
    mapProviderById = new HashMap<>();
    for (Provider provider:providers){
      mapProviderById.put(provider.getId(),provider);
    }
  }

  @Override
  public void reloadPrefix(){
    //get from db
    List<ProviderPrefixDTO> providerPrefixDTOS = providerMapper.getAllPrefix();
    mapProviderIdByPrefix = new HashMap<>();
    for (ProviderPrefixDTO providerPrefixDTO:providerPrefixDTOS){
      mapProviderIdByPrefix.put(providerPrefixDTO.getPrefix(),providerPrefixDTO.getProviderId());
    }
  }

  @Override
  public void checkAllCache(){
    if (mapProviderById.isEmpty()||mapProviderIdByPrefix.isEmpty()||mapCatalogDTOById.isEmpty()
        ||mapListCatalogResponseByProviderId.isEmpty()){
      reloadProvider();
      CompletableFuture<Void> asyncReloadCatalog = asyncAdapter.reloadCatalog();
      CompletableFuture<Void> asyncReloadPrefix = asyncAdapter.reloadPrefix();
      CompletableFuture.allOf(asyncReloadCatalog,asyncReloadPrefix);
    }
  }

  public PulsaCatalog catalogDTOToCatalogAdapter(PulsaCatalogDTO catalogDTO){
    return PulsaCatalog.builder()
        .id(catalogDTO.getId())
        .provider(mapProviderById.get(catalogDTO.getProviderId()))
        .value(catalogDTO.getValue())
        .price(catalogDTO.getPrice())
        .deletedAt(catalogDTO.getDeletedAt())
        .build();
  };
}
