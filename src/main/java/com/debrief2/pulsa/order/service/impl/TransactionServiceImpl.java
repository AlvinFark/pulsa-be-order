package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.model.enums.VoucherType;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.request.IssueVoucherRequest;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.utils.Global;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import com.debrief2.pulsa.order.utils.rpc.RPCClient;
import com.debrief2.pulsa.order.utils.rpc.RPCServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

  @Autowired
  TransactionMapper transactionMapper;
  @Autowired
  ProviderService providerService;
  @Autowired
  RPCServer rpcServer;

//  @Value("${cloudAMQP.url}")
  private String url = "amqp://tfgupaen:DKiggzp1uqZP7do96IFUFgW-SOPNCDl0@emu.rmq.cloudamqp.com/tfgupaen";
  ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public TransactionResponseWithMethodId getTransactionById(long id) throws ServiceException {
    transactionMapper.refreshStatusById(id, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    if (transactionDTO==null){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }
    return transactionDTOtoTransactionResponseWithMethodIdAdapter(transactionDTO);
  }

  @Override
  public TransactionResponse getTransactionByIdByUserId(long id, long userId) throws ServiceException {
    if (!isUserExist(userId)){
      throw new ServiceException(ResponseMessage.member404);
    }
    transactionMapper.refreshStatusById(id, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }
    return transactionDTOtoTransactionResponseAdapter(transactionDTO);
  }

  @Override
  public List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException {
    if (!isUserExist(userId)){
      throw new ServiceException(ResponseMessage.member404);
    }
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
  public OrderResponse createTransaction(long userId, long catalogId, String phoneNumber) throws ServiceException {
    TransactionDTO tr = transactionMapper.checkExistWithin30second(userId,phoneNumber,catalogId,
        getIdByPaymentMethodName(PaymentMethodName.WALLET),getIdByTransactionStatusName(TransactionStatusName.WAITING));
    if (tr!=null){
      throw new ServiceException(ResponseMessage.createTransaction409);
    }
    if (!isUserExist(userId)){
      throw new ServiceException(ResponseMessage.member404);
    }
    if (phoneNumber.charAt(0)!='0'||phoneNumber.length()<9||phoneNumber.length()>13){
      throw new ServiceException(ResponseMessage.createTransaction400phone);
    }
    Provider provider = providerService.getProviderByPrefix(phoneNumber.substring(1,5));
    if (provider==null||provider.getDeletedAt()!=null){
      throw new ServiceException(ResponseMessage.createTransaction404phone);
    }
    PulsaCatalogDTO pulsaCatalogDTO = providerService.getCatalogDTObyId(catalogId);
    if (pulsaCatalogDTO==null||pulsaCatalogDTO.getDeletedAt()!=null) {
      throw new ServiceException(ResponseMessage.createTransaction404catalog);
    }
    if (pulsaCatalogDTO.getProviderId()!=provider.getId()){
      throw new ServiceException(ResponseMessage.createTransaction400Unauthorized);
    }
    TransactionDTO transactionDTOSend = TransactionDTO.builder()
        .userId(userId)
        .methodId(getIdByPaymentMethodName(PaymentMethodName.WALLET))
        .phoneNumber(phoneNumber)
        .catalogId(catalogId)
        .statusId(getIdByTransactionStatusName(TransactionStatusName.WAITING))
        .build();
    transactionMapper.insert(transactionDTOSend);
    TransactionDTO transactionDTO = transactionMapper.getById(transactionDTOSend.getId());
    return transactionDTOtoOrderResponseAdapter(transactionDTO);
  }

  @Override
  public TransactionResponseNoVoucher cancel(long userId, long transactionId) throws ServiceException {
    if (!isUserExist(userId)){
      throw new ServiceException(ResponseMessage.member404);
    }
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
  public List<TransactionOverviewResponse> getHistoryInProgress(long userId, long page) throws ServiceException {
    return getHistory(userId,page,TransactionStatusType.IN_PROGRESS);
  }

  @Override
  public List<TransactionOverviewResponse> getHistoryCompleted(long userId, long page) throws ServiceException {
    return getHistory(userId,page,TransactionStatusType.COMPLETED);
  }

  private List<TransactionOverviewResponse> getHistory(long userId, long page, TransactionStatusType transactionStatusType) throws ServiceException {
    if (!isUserExist(userId)){
      throw new ServiceException(ResponseMessage.member404);
    }
    transactionMapper.refreshStatus(userId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    long offset = (page-1)*10;
    List<TransactionDTO> transactionDTOS = transactionMapper.getAllByUserIdAndStatusTypeIdAndOffset(userId,getIdByTransactionStatusType(transactionStatusType),offset);
    List<TransactionOverviewResponse> transactionOverviewResponses = new ArrayList<>();
    //!!TO-DOs!! make it asynchronous
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

  private TransactionResponse transactionDTOtoTransactionResponseAdapter(TransactionDTO transactionDTO) {
    Voucher voucher = null;
    if (transactionDTO.getVoucherId()!=0){
      voucher = getVoucher(transactionDTO.getVoucherId());
    }
    return transactionDTOtoTransactionResponseAdapter(transactionDTO,voucher);
  }

  private TransactionResponse transactionDTOtoTransactionResponseAdapter(TransactionDTO transactionDTO, Voucher voucher){
    if (voucher!=null&&voucher.getVoucherTypeName()==VoucherType.discount){
      voucher.setDeduction(transactionDTO.getDeduction());
    }
    return TransactionResponse.builder()
        .id(transactionDTO.getId())
        .method(getPaymentMethodNameById(transactionDTO.getMethodId()))
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId())))
        .voucher(voucher)
        .status(getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .updatedAt(transactionDTO.getUpdatedAt())
        .build();
  }

  private TransactionOverviewResponse transactionDTOtoTransactionOverviewResponseAdapter(TransactionDTO transactionDTO){
    PulsaCatalog catalog = providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId()));
    return TransactionOverviewResponse.builder()
        .id(transactionDTO.getId())
        .phoneNumber(transactionDTO.getPhoneNumber())
        .price(catalog.getPrice())
        .voucher(transactionDTO.getDeduction())
        .status(getTransactionStatusNameById(transactionDTO.getStatusId()).name())
        .createdAt(transactionDTO.getCreatedAt())
        .build();
  }

  private TransactionResponseWithMethodId transactionDTOtoTransactionResponseWithMethodIdAdapter(TransactionDTO transactionDTO){
    return TransactionResponseWithMethodId.builder()
        .id(transactionDTO.getId())
        .methodId(transactionDTO.getMethodId())
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId())))
        .status(getTransactionStatusNameById(transactionDTO.getStatusId()))
        .createdAt(transactionDTO.getCreatedAt())
        .updatedAt(transactionDTO.getUpdatedAt())
        .build();
  }

  private OrderResponse transactionDTOtoOrderResponseAdapter(TransactionDTO transactionDTO){
    PulsaCatalogDTO pulsaCatalogDTO = providerService.getCatalogDTObyId(transactionDTO.getCatalogId());
    PulsaCatalog pulsaCatalog = providerService.catalogDTOToCatalogAdapter(pulsaCatalogDTO);
    return OrderResponse.builder()
        .id(transactionDTO.getId())
        .phoneNumber(transactionDTO.getPhoneNumber())
        .catalog(pulsaCatalog)
        .build();
  }

  @Override
  public PayResponse pay(long userId, long transactionId, long methodId, long voucherId) throws ServiceException {
    //!!!!tmp for mocking
    balanceStatic = 24500;

    transactionMapper.refreshStatusById(transactionId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    if (!isUserExist(userId)){
      throw new ServiceException(ResponseMessage.member404);
    }
    PaymentMethodName paymentMethod = getPaymentMethodNameById(methodId);
    if (paymentMethod==null){
      throw new ServiceException(ResponseMessage.pay404method);
    }
    TransactionDTO transactionDTO = transactionMapper.getById(transactionId);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId
        ||getTransactionStatusNameById(transactionDTO.getStatusId())!=TransactionStatusName.WAITING){
      throw new ServiceException(ResponseMessage.pay404transaction);
    }
    transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.VERIFYING));
    transactionMapper.update(transactionDTO);
    PulsaCatalog catalog = providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId()));
    Voucher voucher = null;
    if (voucherId!=0){
      Voucher redeemed = redeem(userId,voucherId,catalog.getPrice(),methodId,catalog.getProvider().getId());
      voucher = getVoucher(voucherId);
      voucher.setValue(redeemed.getValue());
      transactionDTO.setDeduction(catalog.getPrice()-redeemed.getFinalPrice());
    }
    if (catalog.getPrice()-transactionDTO.getDeduction()>getBalance(userId)){
      if (voucherId!=0){
        unRedeem(userId,voucherId);
      }
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
      transactionMapper.update(transactionDTO);
      throw new ServiceException(ResponseMessage.pay400);
    }
    String decreaseMessage = decreaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
    if (!decreaseMessage.equals("success")){
      if (voucherId!=0){
        unRedeem(userId,voucherId);
      }
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
      transactionMapper.update(transactionDTO);
      throw new ServiceException(decreaseMessage);
    }
    HttpStatus response = sendTopUpRequestTo3rdPartyServer(transactionDTO.getPhoneNumber(),catalog);
    if (response==HttpStatus.ACCEPTED){
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.COMPLETED));
      boolean isEligibleToGetVoucher = false;
      if (voucherId==0){
        isEligibleToGetVoucher = eligibleToGetVoucher(userId, catalog.getPrice()-transactionDTO.getDeduction(),
          catalog.getProvider().getId(), voucherId, methodId);
      }
      transactionMapper.update(transactionDTO);
      if (voucher!=null){
        increaseBalance(userId,voucher.getValue());
      }
      if (isEligibleToGetVoucher){
        issue(userId, catalog.getPrice()-transactionDTO.getDeduction(),
            catalog.getProvider().getId(), voucherId, methodId);
      }
      return new PayResponse(getBalance(userId),
          isEligibleToGetVoucher,transactionDTOtoTransactionResponseAdapter(transactionDTO,voucher));
    }
    if (response==HttpStatus.BAD_REQUEST){
      increaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
      unRedeem(userId,voucherId);
      transactionDTO.setVoucherId(0);
      transactionDTO.setDeduction(0);
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.FAILED));
      transactionMapper.update(transactionDTO);
      return new PayResponse(getBalance(userId),false,transactionDTOtoTransactionResponseAdapter(transactionDTO));
    } else {
      return new PayResponse(getBalance(userId)-(catalog.getPrice()-transactionDTO.getDeduction()),
          false,transactionDTOtoTransactionResponseAdapter(transactionDTO));
    }
  }

  ///////////////////////////////////////////// RPC Calls /////////////////////////////////////////////

  private boolean eligibleToGetVoucher(long userId, long price, long providerId, long voucherId, long paymentMethodId){
//    try {
//      IssueVoucherRequest request = new IssueVoucherRequest(userId, price, providerId, voucherId, paymentMethodId);
//      RPCClient rpcClient = new RPCClient(url,"eligibleToGetVoucher");
//      return objectMapper.readValue(rpcClient.call(objectMapper.writeValueAsString(request)),Boolean.class);
//    } catch (Exception e) {
//      return false;
//    }
    return true;
  }

  private HttpStatus sendTopUpRequestTo3rdPartyServer(String phoneNumber, PulsaCatalog catalog){
    return HttpStatus.ACCEPTED;
//    return HttpStatus.BAD_REQUEST;
//    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private boolean isUserExist(long id) {
//    try {
//      RPCClient rpcClient = new RPCClient(url,"getBalance");
//      if (!rpcClient.call(String.valueOf(id)).equals("user not found")){
//        return true;
//      }
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//    return false;
    return true;
  }

  private static long balanceStatic = 24500;
  private long getBalance(long id) {
//    try {
//      RPCClient rpcClient = new RPCClient(url,"getBalance");
//      return Long.parseLong(rpcClient.call(String.valueOf(id)));
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
    return balanceStatic;
  }

  private String decreaseBalance(long userId, long value){
//    try {
//      BalanceRequest request = new BalanceRequest(userId,value);
//      RPCClient rpcClient = new RPCClient(url,"decreaseBalance");
//      return rpcClient.call(objectMapper.writeValueAsString(request));
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//    return "error";
    balanceStatic -= value;
    return "success";
  }

  private void increaseBalance(long userId, long value){
//    try {
//      BalanceRequest request = new BalanceRequest(userId,value);
//      RPCClient rpcClient = new RPCClient(url,"increaseBalance");
//    !!!persistent!!!
//      rpcClient.call(objectMapper.writeValueAsString(request));
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
    balanceStatic +=value;
  }

  private Voucher redeem(long userId, long voucherId, long price, long paymentMethodId, long providerId) throws ServiceException{
//    String message = null;
//    try {
//      RPCClient rpcClient = new RPCClient(url,"redeem");
//      message = rpcClient.call(objectMapper.writeValueAsString(new RedeemRequest(userId,voucherId,price,paymentMethodId,providerId)));
//      Voucher voucher = objectMapper.readValue(message,Voucher.class);
//      if (voucher!=null){
//        return voucher;
//      }
//    } catch (JsonProcessingException e) {
//      throw new ServiceException(message);
//    } catch (Exception e){
//      return null;
//    }
//    return Voucher.builder()
//        .id(voucherId)
//        .finalPrice(price-1000)
//        .value(0)
//        .voucherTypeName(VoucherType.discount)
//        .build();
    return Voucher.builder()
        .id(voucherId)
        .finalPrice(price)
        .value(1000)
        .voucherTypeName(VoucherType.cashback)
        .build();
  }

  private void issue(long userId, long price, long providerId, long voucherId, long paymentMethodId){
//    try {
//      IssueVoucherRequest request = new IssueVoucherRequest(userId, price, providerId, voucherId, paymentMethodId);
//      !!!WITH PERSISTENT!!!
//      RPCClient rpcClient = new RPCClient(url,"issue");
//      rpcClient.call(objectMapper.writeValueAsString(request));
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
  }

  private void unRedeem(long userId, long voucherId){
//    try {
//      UnRedeemRequest request = new UnRedeemRequest(userId,voucherId);
//      !!!WITH PERSISTENT!!!
//      RPCClient rpcClient = new RPCClient(url,"unredeem");
//      rpcClient.call(objectMapper.writeValueAsString(request));
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
  }

  private Voucher getVoucher(long id){
//    try {
//      RPCClient rpcClient = new RPCClient(url,"getVoucherDetail");
//      Voucher voucher = objectMapper.readValue(rpcClient.call(String.valueOf(id)),Voucher.class);
//      if (voucher!=null){
//        return voucher;
//      }
//    } catch (Exception e) {
//    }
//    return null;
//    return Voucher.builder()
//        .id(1)
//        .name("voucher diskon")
//        .maxDeduction(10000)
//        .voucherTypeName(VoucherType.discount)
//        .active(true)
//        .build();
    return Voucher.builder()
        .id(1)
        .name("voucher cashback")
        .maxDeduction(0)
        .voucherTypeName(VoucherType.cashback)
        .active(true)
        .build();
  }
}