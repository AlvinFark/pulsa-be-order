package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.exception.OtherServiceException;
import com.debrief2.pulsa.order.exception.ServiceUnreachableException;
import com.debrief2.pulsa.order.model.*;
import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.model.enums.VoucherType;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.request.BalanceRequest;
import com.debrief2.pulsa.order.payload.request.IssueVoucherRequest;
import com.debrief2.pulsa.order.payload.request.RedeemRequest;
import com.debrief2.pulsa.order.payload.request.UnRedeemRequest;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.utils.Global;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import com.debrief2.pulsa.order.utils.rpc.RPCClient;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class TransactionServiceImpl implements TransactionService {

  @Autowired
  TransactionMapper transactionMapper;
  @Autowired
  ProviderService providerService;

  private final String promotionUrl = "amqp://ynjauqav:K83KvUARdw7DyYLJF2_gt2RVzO-NS2YM@lively-peacock.rmq.cloudamqp.com/ynjauqav";
  private final String memberUrl = "amqp://ynjauqav:K83KvUARdw7DyYLJF2_gt2RVzO-NS2YM@lively-peacock.rmq.cloudamqp.com/ynjauqav";
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
    try {
      if (!userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }
    transactionMapper.refreshStatusById(id, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }
    try {
      return transactionDTOtoTransactionResponseAdapter(transactionDTO);
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to get detail voucher");
    }
  }

  @Override
  public List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException {
    try {
      if (!userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
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
    try {
      if (!userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
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
    try {
      if (!userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
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
    try {
      if (!userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }
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

  private TransactionResponse transactionDTOtoTransactionResponseAdapter(TransactionDTO transactionDTO) throws ServiceUnreachableException, OtherServiceException {
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
    //refresh transaction status
    transactionMapper.refreshStatusById(transactionId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));

    //check user exist, if not return not exist
    //if error because other service, return error message and do nothing
    try {
      if (!userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }

    //check method valid
    PaymentMethodName paymentMethod = getPaymentMethodNameById(methodId);
    if (paymentMethod==null){
      throw new ServiceException(ResponseMessage.pay404method);
    }

    //check if transaction available to pay (exist, user's, and still waiting state)
    TransactionDTO transactionDTO = transactionMapper.getById(transactionId);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId
        ||getTransactionStatusNameById(transactionDTO.getStatusId())!=TransactionStatusName.WAITING){
      throw new ServiceException(ResponseMessage.pay404transaction);
    }

    //change status to verifying
    transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.VERIFYING));
    transactionMapper.update(transactionDTO);

    //get catalog detail (POSSIBLE ERROR IF CATALOG REMOVED IN BETWEEN ORDER AND PAY, RETURN FAILED, CHANGE ADAPTER FIRST)
    PulsaCatalog catalog = providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId()));

    //redeeming voucher if using voucher
    //if redeeming process error, return the exact message because it's either error message from promotion or connection error
    //and revert transaction to waiting
    //if redeem success then get the details
    //if getting details failed (really it shouldn't unless promotion down after redeeming)
    //then do revert status to waiting and unredeem
    //if unredeem failed, then throw notice user to contact customer service (this case actually would never happened unless
    //because unredeem is using persistent message, unless case like change MQ url, rejected connection)
    //only if everything successfully executed, add details from redeemed into voucher and transaction
    Voucher voucher = null;
    if (voucherId!=0){
      try {
        Voucher redeemed = redeem(userId,voucherId,catalog.getPrice(),methodId,catalog.getProvider().getId());
        try {
          voucher = getVoucher(voucherId);
        } catch (ServiceUnreachableException | OtherServiceException f){
          try {
            transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
            transactionMapper.update(transactionDTO);
            unRedeem(userId,voucherId);
            throw new ServiceException(f.getMessage()+" when try to get voucher details, voucher has been redeemed");
          } catch (ServiceUnreachableException | OtherServiceException g) {
            throw new ServiceException(f.getMessage()+" when try to get voucher details, and failed to unredeem voucher");
          }
        }
        voucher.setValue(redeemed.getValue());
        transactionDTO.setVoucherId(voucherId);
        transactionDTO.setDeduction(catalog.getPrice()-redeemed.getFinalPrice());
      } catch (ServiceUnreachableException | OtherServiceException e){
        transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
        transactionMapper.update(transactionDTO);
        throw new ServiceException(e.getMessage()); //message from promotion
      }
    }

    //first, try to get user balance, if error, revert transaction
    //if success check if balance enough
    //if not enough and using voucher try to unredeem, the details of error same as before
    //if not enough also revert transaction and send error not enough balance
    //the balance saved in case of further error
    long balance = 0;
    try {
      balance = getBalance(userId);
      if (catalog.getPrice()-transactionDTO.getDeduction()>balance){
        transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
        transactionDTO.setVoucherId(0);
        transactionDTO.setDeduction(0);
        transactionMapper.update(transactionDTO);
        if (voucherId!=0){
          try {
            unRedeem(userId,voucherId);
          } catch (ServiceUnreachableException | OtherServiceException e) {
            e.printStackTrace();
            throw new ServiceException(ResponseMessage.pay400 + ", and " + e.getMessage() + " when try to unredeem voucher");
          }
        }
        throw new ServiceException(ResponseMessage.pay400);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
      transactionDTO.setVoucherId(0);
      transactionDTO.setDeduction(0);
      transactionMapper.update(transactionDTO);
      e.printStackTrace();
      throw new ServiceException(e.getMessage() + " when try to check enough balance");
    }

    //try to decrease balance,
    //if error revert transaction and unredeem voucher if using voucher
    //if unredeeming error, same as before
    try {
      decreaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
      balance -= catalog.getPrice()-transactionDTO.getDeduction();
    } catch (ServiceUnreachableException | OtherServiceException e) {
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
      transactionDTO.setVoucherId(0);
      transactionDTO.setDeduction(0);
      transactionMapper.update(transactionDTO);
      if (voucherId!=0){
        try {
          unRedeem(userId,voucherId);
        } catch (ServiceUnreachableException | OtherServiceException f) {
          e.printStackTrace();
          throw new ServiceException(f.getMessage() + " when try to unredeem voucher, when try to decrease balance failed");
        }
      }
      throw new ServiceException(e.getMessage() + " when try to decrease balance");
    }

    //send the mobile recharge request to 3rd party provider, there's 3 possibility: accepted,rejected, and internal server error
    HttpStatus response = sendTopUpRequestTo3rdPartyServer(transactionDTO.getPhoneNumber(),catalog);

    //case accepted
    if (response==HttpStatus.ACCEPTED){
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.COMPLETED));
      //if not using voucher, check whether available to get voucher
      //if error when checking, do nothing and set it as not getting voucher
      //(not possible to send message since the return to mobile domain already set as the object, need to discuss first)
      //but still will call issue voucher so user still get voucher even though not notified
      boolean callIssueVoucher = true;
      boolean isEligibleToGetVoucher = false;
      if (voucherId==0){
        try {
          isEligibleToGetVoucher = eligibleToGetVoucher(userId, catalog.getPrice()-transactionDTO.getDeduction(),
            catalog.getProvider().getId(), voucherId, methodId);
          callIssueVoucher = isEligibleToGetVoucher;
        } catch (ServiceUnreachableException | OtherServiceException e) {
          e.printStackTrace();
        }
      }

      //update the database
      transactionMapper.update(transactionDTO);

      //give cashback if using voucher on cashback type by increasing balance
      //if increase balance error (small possibility since it's persistent)
      //do nothing (again because the return format)
      if (voucherId!=0&&voucher.getValue()>0){
        try {
          increaseBalance(userId,voucher.getValue());
          balance += voucher.getValue();
        } catch (ServiceUnreachableException | OtherServiceException e) {
          e.printStackTrace();
        }
      }
      //issue voucher if possible to get one, persistent so less likely to get error
      if (callIssueVoucher){
        try {
          issue(userId, catalog.getPrice()-transactionDTO.getDeduction(),
              catalog.getProvider().getId(), voucherId, methodId);
        } catch (ServiceUnreachableException | OtherServiceException e) {
          e.printStackTrace();
        }
      }

      //return details transaction, whether get voucher or not, and updated balance
      //if after all this process, failed to  get actual balance, the balance saved before would come in handy
      try {
        return new PayResponse(getBalance(userId),
            isEligibleToGetVoucher,transactionDTOtoTransactionResponseAdapter(transactionDTO,voucher));
      } catch (ServiceUnreachableException | OtherServiceException e) {
        return new PayResponse(balance,
            isEligibleToGetVoucher,transactionDTOtoTransactionResponseAdapter(transactionDTO,voucher));
      }
    }

    //case rejected
    if (response==HttpStatus.BAD_REQUEST){
      //revert all transaction details but change to failed so that user won't try to pay again (it is rejected for a reason)
      transactionDTO.setVoucherId(0);
      transactionDTO.setDeduction(0);
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.FAILED));
      transactionMapper.update(transactionDTO);
      //increase balance back and unredeem voucher back persistently
      try {
        increaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
        balance += catalog.getPrice()-transactionDTO.getDeduction();
      } catch (ServiceUnreachableException | OtherServiceException e) {
        e.printStackTrace();
      }
      try {
        unRedeem(userId,voucherId);
      } catch (ServiceUnreachableException | OtherServiceException e) {
        e.printStackTrace();
      }
    }

    //the return for failed is the same format as rejected
    try {
      return new PayResponse(getBalance(userId),false,transactionDTOtoTransactionResponseAdapter(transactionDTO,null));
    } catch (ServiceUnreachableException | OtherServiceException e) {
      return new PayResponse(balance,false,transactionDTOtoTransactionResponseAdapter(transactionDTO,null));
    }
  }

  /////////////////////////////////////////// 3rd Party Calls //////////////////////////////////////////

  private HttpStatus sendTopUpRequestTo3rdPartyServer(String phoneNumber, PulsaCatalog catalog){
    return HttpStatus.ACCEPTED;
//    return HttpStatus.BAD_REQUEST;
//    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  ///////////////////////////////////////////// RPC Calls /////////////////////////////////////////////

  @Override
  public void tmpDebugging(){
    try {
      OrderResponse orderResponse = createTransaction(1,14, "085200872725");
      System.out.println(orderResponse.toString());
      PayResponse payResponse = pay(1, orderResponse.getId(), 1, 0);
      System.out.println(payResponse.toString());
    } catch (ServiceException e) {
      e.printStackTrace();
      System.out.println(e.getMessage());
    }
  }

  private boolean userExist(long id) throws ServiceUnreachableException, OtherServiceException {
    try {
      return !RPCClient.call(memberUrl,"getBalance",String.valueOf(id)).equals("user not found");
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private long getBalance(long id) throws ServiceUnreachableException, OtherServiceException {
    try {
      String response = RPCClient.call(memberUrl,"getBalance",String.valueOf(id));
      return Long.parseLong(response.substring(1,response.length()-1));
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private void decreaseBalance(long userId, long value) throws ServiceUnreachableException, OtherServiceException {
    try {
      BalanceRequest request = new BalanceRequest(userId,value);
      String message = RPCClient.call(memberUrl,"decreaseBalance",objectMapper.writeValueAsString(request));
      if (!message.equals("\"success\"")){
        throw new OtherServiceException(message);
      }
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private void increaseBalance(long userId, long value) throws ServiceUnreachableException, OtherServiceException {
    try {
      BalanceRequest request = new BalanceRequest(userId,value);
      //switch to persistent later
      RPCClient.call(memberUrl,"increaseBalance",objectMapper.writeValueAsString(request));
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private boolean eligibleToGetVoucher(long userId, long price, long providerId, long voucherId, long paymentMethodId) throws ServiceUnreachableException, OtherServiceException {
    try {
      IssueVoucherRequest request = new IssueVoucherRequest(userId, price, providerId, voucherId, paymentMethodId);
      return objectMapper.readValue(RPCClient.call(promotionUrl,"eligibleToGetVoucher",objectMapper.writeValueAsString(request)),Boolean.class);
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private Voucher redeem(long userId, long voucherId, long price, long paymentMethodId, long providerId) throws OtherServiceException, ServiceUnreachableException {
    String message = "";
    try {
      RedeemRequest request = new RedeemRequest(userId,voucherId,price,paymentMethodId,providerId);
      message = RPCClient.call(promotionUrl,"redeem",objectMapper.writeValueAsString(request));
      return objectMapper.readValue(message,Voucher.class);
    } catch (JsonParseException e) {
      throw new OtherServiceException(message);
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private void unRedeem(long userId, long voucherId) throws ServiceUnreachableException, OtherServiceException {
    try {
      UnRedeemRequest request = new UnRedeemRequest(userId,voucherId);
      //switch into persistent
      RPCClient.call(promotionUrl,"unredeem",objectMapper.writeValueAsString(request));
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private void issue(long userId, long price, long providerId, long voucherId, long paymentMethodId) throws ServiceUnreachableException, OtherServiceException {
    try {
      IssueVoucherRequest request = new IssueVoucherRequest(userId, price, providerId, voucherId, paymentMethodId);
      //switch into persistent
      RPCClient.call(promotionUrl,"issue",objectMapper.writeValueAsString(request));
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }

  private Voucher getVoucher(long id) throws ServiceUnreachableException, OtherServiceException {
    String message = "";
    try {
      message = RPCClient.call(promotionUrl,"getVoucherDetail",String.valueOf(id));
      return objectMapper.readValue(message,Voucher.class);
    } catch (JsonParseException e) {
      return null;
    } catch (IOException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionIO);
    } catch (TimeoutException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    } catch (Exception e) {
      e.printStackTrace();
      throw new OtherServiceException(e.getClass().getSimpleName());
    }
  }
}