package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.exception.OtherServiceException;
import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.exception.ServiceUnreachableException;
import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.model.Transaction;
import com.debrief2.pulsa.order.model.Voucher;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusName;
import com.debrief2.pulsa.order.model.enums.TransactionStatusType;
import com.debrief2.pulsa.order.payload.dto.PulsaCatalogDTO;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.service.RPCService;
import com.debrief2.pulsa.order.service.TransactionAdapter;
import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.utils.Global;
import com.debrief2.pulsa.order.utils.ResponseMessage;
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
  RPCService rpcService;
  @Autowired
  TransactionAdapter transactionAdapter;

  //for promotion service
  @Override
  public TransactionWithMethodId getTransactionById(long id) throws ServiceException {
    //first refresh transaction status if this transaction
    transactionMapper.refreshStatusById(id, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    //get the transaction
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    //return not found if received null as response
    if (transactionDTO==null){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }
    //return the transaction that has been converted to transaction model
    return transactionAdapter.transactionDTOtoTransactionWithMethodIdAdapter(transactionDTO);
  }

  @Override
  public Transaction getTransactionByIdByUserId(long id, long userId) throws ServiceException {
    try {
      if (!rpcService.userExist(userId)){
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
    Voucher voucher = null;
    try {
      if (transactionDTO.getVoucherId()!=0){
        voucher = rpcService.getVoucher(transactionDTO.getVoucherId());
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to get detail voucher");
    }
    return transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher);
  }

  @Override
  public List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException {
    try {
      if (!rpcService.userExist(userId)){
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
      if (!rpcService.userExist(userId)){
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
    return transactionAdapter.transactionDTOtoOrderResponseAdapter(transactionDTO);
  }

  @Override
  public TransactionNoVoucher cancel(long userId, long transactionId) throws ServiceException {
    try {
      if (!rpcService.userExist(userId)){
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
    return TransactionNoVoucher.builder()
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
  public PayResponse pay(long userId, long transactionId, long methodId, long voucherId) throws ServiceException {
    //refresh transaction status
    transactionMapper.refreshStatusById(transactionId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));

    //check user exist, if not return not exist
    //if error because other service, return error message and do nothing
    try {
      if (!rpcService.userExist(userId)){
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
        Voucher redeemed = rpcService.redeem(userId,voucherId,catalog.getPrice(),methodId,catalog.getProvider().getId());
        try {
          voucher = rpcService.getVoucher(voucherId);
        } catch (ServiceUnreachableException | OtherServiceException f){
          try {
            transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
            transactionMapper.update(transactionDTO);
            rpcService.unRedeem(userId,voucherId);
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
    long balance;
    try {
      balance = rpcService.getBalance(userId);
      if (catalog.getPrice()-transactionDTO.getDeduction()>balance){
        transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
        transactionDTO.setVoucherId(0);
        transactionDTO.setDeduction(0);
        transactionMapper.update(transactionDTO);
        if (voucherId!=0){
          try {
            rpcService.unRedeem(userId,voucherId);
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
      rpcService.decreaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
      balance -= catalog.getPrice()-transactionDTO.getDeduction();
    } catch (ServiceUnreachableException | OtherServiceException e) {
      transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.WAITING));
      transactionDTO.setVoucherId(0);
      transactionDTO.setDeduction(0);
      transactionMapper.update(transactionDTO);
      if (voucherId!=0){
        try {
          rpcService.unRedeem(userId,voucherId);
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
          isEligibleToGetVoucher = rpcService.eligibleToGetVoucher(userId, catalog.getPrice()-transactionDTO.getDeduction(),
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
          rpcService.increaseBalance(userId,voucher.getValue());
          balance += voucher.getValue();
        } catch (ServiceUnreachableException | OtherServiceException e) {
          e.printStackTrace();
        }
      }
      //issue voucher if possible to get one, persistent so less likely to get error
      if (callIssueVoucher){
        try {
          rpcService.issue(userId, catalog.getPrice()-transactionDTO.getDeduction(),
              catalog.getProvider().getId(), voucherId, methodId);
        } catch (ServiceUnreachableException | OtherServiceException e) {
          e.printStackTrace();
        }
      }

      //return details transaction, whether get voucher or not, and updated balance
      //if after all this process, failed to  get actual balance, the balance saved before would come in handy
      try {
        return new PayResponse(rpcService.getBalance(userId),
            isEligibleToGetVoucher, transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher));
      } catch (ServiceUnreachableException | OtherServiceException e) {
        return new PayResponse(balance,
            isEligibleToGetVoucher, transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher));
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
        rpcService.increaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
        balance += catalog.getPrice()-transactionDTO.getDeduction();
      } catch (ServiceUnreachableException | OtherServiceException e) {
        e.printStackTrace();
      }
      try {
        rpcService.unRedeem(userId,voucherId);
      } catch (ServiceUnreachableException | OtherServiceException e) {
        e.printStackTrace();
      }
    }

    //the return for failed is the same format as rejected
    try {
      return new PayResponse(rpcService.getBalance(userId),false, transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,null));
    } catch (ServiceUnreachableException | OtherServiceException e) {
      return new PayResponse(balance,false, transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,null));
    }
  }

  @Override
  public List<TransactionOverview> getHistoryInProgress(long userId, long page) throws ServiceException {
    return getHistory(userId,page,TransactionStatusType.IN_PROGRESS);
  }

  @Override
  public List<TransactionOverview> getHistoryCompleted(long userId, long page) throws ServiceException {
    return getHistory(userId,page,TransactionStatusType.COMPLETED);
  }

  private List<TransactionOverview> getHistory(long userId, long page, TransactionStatusType transactionStatusType) throws ServiceException {
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }
    transactionMapper.refreshStatus(userId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
    long offset = (page-1)*10;
    List<TransactionDTO> transactionDTOS = transactionMapper.getAllByUserIdAndStatusTypeIdAndOffset(userId,getIdByTransactionStatusType(transactionStatusType),offset);
    List<TransactionOverview> transactionOverview = new ArrayList<>();
    for (TransactionDTO transactionDTO:transactionDTOS) {
      transactionOverview.add(transactionAdapter.transactionDTOtoTransactionOverviewAdapter(transactionDTO));
    }
    return transactionOverview;
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

  @Override
  public TransactionStatusName getTransactionStatusNameById(long id){
    try {
      return TransactionStatusName.values()[(int) id-1];
    } catch (ArrayIndexOutOfBoundsException e) {
      return null;
    }
  }

  private long getIdByTransactionStatusName(TransactionStatusName transactionStatusName){
    return transactionStatusName.ordinal()+1;
  }

  private long getIdByTransactionStatusType(TransactionStatusType transactionStatusType){
    return transactionStatusType.ordinal()+1;
  }

  /////////////////////////////////////////// 3rd Party Calls //////////////////////////////////////////
  private HttpStatus sendTopUpRequestTo3rdPartyServer(String phoneNumber, PulsaCatalog catalog){
    return HttpStatus.ACCEPTED;
//    return HttpStatus.BAD_REQUEST;
//    return HttpStatus.INTERNAL_SERVER_ERROR;
  }
}