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
import com.debrief2.pulsa.order.service.*;
import com.debrief2.pulsa.order.utils.Global;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    //return the transaction that has been converted to transaction model, this adapter also get catalog and provider detail
    return transactionAdapter.transactionDTOtoTransactionWithMethodIdAdapter(transactionDTO);
  }

  //when user check details of transaction
  @Override
  public Transaction getTransactionByIdByUserId(long id, long userId) throws ServiceException {
    //validate user exist
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }

    //refresh status so that it will updated if expired
    transactionMapper.refreshStatusById(id, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));

    //get the transaction
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    //return if null or not user's transaction
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.getTransactionById404);
    }

    //get the details for voucher
    //if error from calling other service, just sent error message since it's the method not changing anything
    Voucher voucher = null;
    try {
      if (transactionDTO.getVoucherId()!=0){
        voucher = rpcService.getVoucher(transactionDTO.getVoucherId());
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to get detail voucher");
    }

    //return the transaction details, this adapter also get catalog and provider detail
    return transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher);
  }

  //when user open mobile recharge page, they'll see their history of numbers the've used before as recommendation
  @Override
  public List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException {
    //validate user exist
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }

    //get 10 latest created transaction from db
    List<TransactionDTO> transactionDTOS = transactionMapper.getTenRecentByUserId(userId);
    //placeholder for returned data
    List<RecentNumberResponse> recentNumberResponses = new ArrayList<>();

    //for each transaction, get the detail provider and wrap it into return format
    //it doesn't matter if provider is soft deleted since it's a history kind a thing
    for (TransactionDTO transactionDTO:transactionDTOS) {
      Provider provider = providerService.getProviderByPrefix(transactionDTO.getPhoneNumber().substring(1,5));
      RecentNumberResponse recentNumberResponse = RecentNumberResponse.builder()
          .number(transactionDTO.getPhoneNumber())
          .provider(provider)
          .build();
      //this is to make frontend job easier, they can set the view from date data which is derived from updatedAt
      //or createdAt if not updated at data
      if (transactionDTO.getUpdatedAt()==null){
        recentNumberResponse.setDate(transactionDTO.getCreatedAt());
      } else {
        recentNumberResponse.setDate(transactionDTO.getUpdatedAt());
      }
      recentNumberResponses.add(recentNumberResponse);
    }

    //return it. No error message for empty data just empty array as requested from frontend
    return recentNumberResponses;
  }

  //when user clicked on top up option it will automatically create order
  @Override
  @Transactional
  public OrderResponse createTransaction(long userId, long catalogId, String phoneNumber) throws ServiceException {

    //check if similar transaction already created within 30 seconds, for possible mistake or other reasons
    //return error message if exist
    TransactionDTO tr = transactionMapper.checkExistWithin30second(userId,phoneNumber,catalogId,
        getIdByPaymentMethodName(PaymentMethodName.WALLET),getIdByTransactionStatusName(TransactionStatusName.WAITING));
    if (tr!=null){
      throw new ServiceException(ResponseMessage.createTransaction409);
    }

    //validate user
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }
    //validate phone number
    if (phoneNumber.length()<9||phoneNumber.length()>13||phoneNumber.charAt(0)!='0'){
      throw new ServiceException(ResponseMessage.createTransaction400phone);
    }
    try {
      Long.parseLong(phoneNumber.substring(1));
    } catch (NumberFormatException e){
      throw new ServiceException(ResponseMessage.createTransaction400phone);
    }

    //get provider by using the phone's prefix, if not exist or deleted return error
    Provider provider = providerService.getProviderByPrefix(phoneNumber.substring(1,5));
    if (provider==null||provider.getDeletedAt()!=null){
      throw new ServiceException(ResponseMessage.createTransaction404phone);
    }

    //get catalog detail, if not exist or soft deleted return error
    PulsaCatalogDTO pulsaCatalogDTO = providerService.getCatalogDTObyId(catalogId);
    if (pulsaCatalogDTO==null||pulsaCatalogDTO.getDeletedAt()!=null) {
      throw new ServiceException(ResponseMessage.createTransaction404catalog);
    }

    //if catalog is not for this phone, return error
    if (pulsaCatalogDTO.getProviderId()!=provider.getId()){
      throw new ServiceException(ResponseMessage.createTransaction400Unauthorized);
    }

    //send it to db
    TransactionDTO transactionDTOSend = TransactionDTO.builder()
        .userId(userId)
        .methodId(getIdByPaymentMethodName(PaymentMethodName.WALLET))
        .phoneNumber(phoneNumber)
        .catalogId(catalogId)
        .statusId(getIdByTransactionStatusName(TransactionStatusName.WAITING))
        .build();
    transactionMapper.insert(transactionDTOSend);

    //return the details, the id will be automatically updated by mybatis
    TransactionDTO transactionDTO = transactionMapper.getById(transactionDTOSend.getId());
    return transactionAdapter.transactionDTOtoOrderResponseAdapter(transactionDTO);
  }

  //for when user clicked on cancel
  @Override
  @Transactional
  public TransactionNoVoucher cancel(long userId, long transactionId) throws ServiceException {
    //validate user exist
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }

    //refresh transaction status
    transactionMapper.refreshStatusById(transactionId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));

    //if null or not user's transaction send error
    TransactionDTO transactionDTO = transactionMapper.getById(transactionId);
    if (transactionDTO==null||transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.cancelTransaction404);
    }

    //if transaction is not in waiting state return error message
    if (transactionDTO.getStatusId()!= getIdByTransactionStatusName(TransactionStatusName.WAITING)){
      throw new ServiceException(ResponseMessage.cancelTransaction400);
    }

    //update to the database
    transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.CANCELED));
    transactionDTO.setVoucherId(0);
    transactionMapper.update(transactionDTO);
    System.out.println(transactionDTO);

    //need to get updated at detail from db
    TransactionDTO td = transactionMapper.getById(transactionId);

    //return
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

  //when user click pay
  @Override
  @Transactional
  public PayResponse pay(long userId, long transactionId, long methodId, long voucherId) throws ServiceException {
    //check user exist, if not return not exist
    //if error because other service, return error message and do nothing
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }

    //refresh transaction status
    transactionMapper.refreshStatusById(transactionId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));

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

  //just wrapper so it's easier to call from mobile domain
  @Override
  public List<TransactionOverview> getHistoryInProgress(long userId, long page) throws ServiceException {
    return getHistory(userId,page,TransactionStatusType.IN_PROGRESS);
  }

  //just wrapper so it's easier to call from mobile domain
  @Override
  public List<TransactionOverview> getHistoryCompleted(long userId, long page) throws ServiceException {
    return getHistory(userId,page,TransactionStatusType.COMPLETED);
  }

  //the real flow when user check on history
  private List<TransactionOverview> getHistory(long userId, long page, TransactionStatusType transactionStatusType) throws ServiceException {
    //validate page
    if (page<1){
      throw new ServiceException(ResponseMessage.generic400);
    }

    //validate user
    try {
      if (!rpcService.userExist(userId)){
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage()+" when try to check user exist");
    }

    //refresh transaction for this user
    transactionMapper.refreshStatus(userId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));

    //calculate the offset
    long offset = (page-1)*10;

    //get the data by parameters
    List<TransactionDTO> transactionDTOS = transactionMapper.getAllByUserIdAndStatusTypeIdAndOffset(userId,getIdByTransactionStatusType(transactionStatusType),offset);

    //convert into accepted format, no validation for catalog etc since the data are from db and even
    //if the data soft deleted, it's still better to show since it's a history thing
    List<TransactionOverview> transactionOverview = new ArrayList<>();
    for (TransactionDTO transactionDTO:transactionDTOS) {
      transactionOverview.add(transactionAdapter.transactionDTOtoTransactionOverviewAdapter(transactionDTO));
    }

    //return, same as recent number, no error if empty just return empty array
    return transactionOverview;
  }

  ////////////////////////////////// HELPER ////////////////////////////////////
  //these are just helper for easier extraction of data from enums

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