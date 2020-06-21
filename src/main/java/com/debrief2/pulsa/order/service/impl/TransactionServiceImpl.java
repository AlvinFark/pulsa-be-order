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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
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
  @Autowired
  AsyncAdapter asyncAdapter;

  //for promotion service
  @Override
  public TransactionWithMethodId getTransactionById(long id) throws ServiceException {
    //first refresh transaction status if this transaction
    refreshById(id);

    //get the transaction, method include return error if not found
    TransactionDTO transactionDTO = getAndValidateTransactionDTO(id);
    //return the transaction that has been converted to transaction model, this adapter also get catalog and provider detail
    return transactionAdapter.transactionDTOtoTransactionWithMethodIdAdapter(transactionDTO);
  }

  //when user check details of transaction
  @Override
  public Transaction getTransactionByIdByUserId(long id, long userId) throws ServiceException {
    //call member domain, include return error if not found or error when send request
    validateUser(userId);

    //refresh status so that it will be updated if expired
    refreshById(id);

    //get the transaction, method include return error if not found
    TransactionDTO transactionDTO = getAndValidateTransactionDTO(id);
    //validate it's user's transaction, include return error if don't belong to user
    validateTransactionBelongToUser(userId, transactionDTO);
    //get the details for voucher from promotion domain, include return error if error when send request
    Voucher voucher = getAndValidateVoucher(transactionDTO.getVoucherId());

    //return the transaction details, this adapter also get catalog and provider detail
    return transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher);
  }

  //when user open mobile recharge page, they'll see their history of numbers the've used before as recommendation
  @Override
  public List<RecentNumberResponse> getRecentNumber(long userId) throws ServiceException {
    //call member domain, include return error if not found or error when send request
    validateUser(userId);

    //get 10 latest created transaction from db
    List<TransactionDTO> transactionDTOS = transactionMapper.getTenRecentByUserId(userId);
    //placeholder for returned data
    List<RecentNumberResponse> recentNumberResponses = new ArrayList<>();

    //for each transaction, get the detail provider and wrap it into return format
    //it doesn't matter if provider is soft deleted since it's a history thing
    for (TransactionDTO transactionDTO:transactionDTOS) {
      recentNumberResponses.add(transactionAdapter.transactionDTOtoRecentNumberResponseAdapter(transactionDTO));
    }

    //return it. No error message for empty data just empty array as requested from frontend
    return recentNumberResponses;
  }

  //when user clicked on top up option it will automatically create order
  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = ServiceException.class)
  public OrderResponse createTransaction(long userId, long catalogId, String phoneNumber) throws ServiceException {
    //check if similar transaction already created within 30 seconds, for possible mistake or other reasons
    //return error message if exist
    TransactionDTO tr = transactionMapper.checkExistWithin30second(userId,phoneNumber,catalogId, getIdByPaymentMethodName(PaymentMethodName.WALLET),getIdByTransactionStatusName(TransactionStatusName.WAITING));
    if (tr!=null) throw new ServiceException(ResponseMessage.createTransaction409);

    //call member domain, include return error if not found or error when send request
    validateUser(userId);
    //validate phone number, include return error if has wrong length, not started with 0 or non numerical
    validatePhoneNumber(phoneNumber);

    //get provider by using the phone's prefix, include return error if not exist or deleted
    Provider provider = getAndValidateProvider(phoneNumber);
    //get catalog detail, include return error if not exist or soft deleted
    PulsaCatalogDTO pulsaCatalogDTO = getAndValidatePulsaCatalogDTO(catalogId);
    //if catalog is not for this phone, return error
    if (pulsaCatalogDTO.getProviderId()!=provider.getId()) throw new ServiceException(ResponseMessage.catalog400);

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
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public TransactionNoVoucher cancel(long userId, long transactionId) throws ServiceException {
    //call member domain, include return error if not found or error when send request
    validateUser(userId);

    //refresh transaction status
    refreshById(transactionId);

    //get the transaction, include return error if not found
    TransactionDTO transactionDTO = getAndValidateTransactionDTO(transactionId);
    //validate it's user's transaction, include return error if not
    validateTransactionBelongToUser(userId, transactionDTO);

    //if transaction is not in waiting state return error message
    if (transactionDTO.getStatusId()!=getIdByTransactionStatusName(TransactionStatusName.WAITING))
      throw new ServiceException(ResponseMessage.cancelTransaction400);

    //update to the database
    transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.CANCELED));
    transactionMapper.update(transactionDTO);

    //need to get the updatedAt detail from db
    TransactionDTO td = transactionMapper.getById(transactionId);
    transactionDTO.setUpdatedAt(td.getUpdatedAt());
    //return
    return transactionAdapter.transactionDTOtoTransactionNoVoucherAdapter(transactionDTO);
  }

  //when user click pay
  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = ServiceException.class)
  public PayResponse pay(long userId, long transactionId, long methodId, long voucherId) throws ServiceException {
    //refresh transaction status
    refreshById(transactionId);

    //check user exist, if not return not exist
    //if error because other service, return error message and do nothing
    validateUser(userId);
    //validate method, return error if not found
    validateMethod(methodId);

    //get the transaction, include return error if not found
    TransactionDTO transactionDTO = getAndValidateTransactionDTO(transactionId);
    //validate it's user's transaction, return error if not
    validateTransactionBelongToUser(userId, transactionDTO);
    //check if transaction still in waiting state, return error if not
    if (getTransactionStatusNameById(transactionDTO.getStatusId())!=TransactionStatusName.WAITING)
      throw new ServiceException(ResponseMessage.transaction404);

    //get catalog detail
    PulsaCatalog catalog = providerService.catalogDTOToCatalogAdapter(providerService.getCatalogDTObyId(transactionDTO.getCatalogId()));
    //redeem and then get voucher detail, include errors and reverting voucher by unRedeem
    Voucher voucher = redeemAndGetVoucherDetails(userId, methodId, voucherId, catalog.getPrice(), catalog.getProvider().getId());
    if (voucher!=null) transactionDTO.setDeduction(voucher.getDeduction());
    transactionDTO.setVoucherId(voucherId);

    long balance = 0;
    boolean isEligibleToGetVoucher = false;
    switch (getPaymentMethodNameById(methodId)){
      case WALLET:
        //get user balance, include return error and unRedeeming if not enough balance or error calling member domain
        //the balance saved in case of further error
        balance = getAndValidateEnoughBalance(userId,catalog.getPrice(),transactionDTO.getDeduction(),voucherId);
        //decrease balance, include error and unredeeming if failed to decrease or connect
        balance = decreaseBalance(balance,catalog.getPrice(),transactionDTO.getDeduction(),userId,voucherId);

        //send the mobile recharge request to 3rd party provider, there's 3 possibility: accepted,rejected, and internal server error
        HttpStatus response = rpcService.sendTopUpRequestTo3rdPartyServer(transactionDTO.getPhoneNumber(),catalog);

        //case accepted
        if (response==HttpStatus.ACCEPTED){
          transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.COMPLETED));
          //if not using voucher, check whether available to get voucher
          //if error when checking, do nothing and set it as not getting voucher
          //but still will call issue voucher so user still get voucher even though not notified
          boolean callIssueVoucher = false;
          if (voucherId==0){
            callIssueVoucher = true;
            try {
              isEligibleToGetVoucher = rpcService.eligibleToGetVoucher(userId, catalog.getPrice()-transactionDTO.getDeduction(),
                  catalog.getProvider().getId(), voucherId, methodId);
              callIssueVoucher = isEligibleToGetVoucher;
            } catch (ServiceUnreachableException ignored) {}
          }

          //update the database
          transactionMapper.update(transactionDTO);
          //give cashback if using voucher on cashback type by increasing balance
          //if increase balance error (small possibility since it's persistent)
          //do nothing (again because the return format)
          if (voucherId!=0&&voucher.getValue()>0){
            asyncAdapter.increaseBalance(userId,voucher.getValue());
            balance += voucher.getValue();
          }
          //issue voucher if possible to get one, persistent so less likely to get error
          if (callIssueVoucher)
            asyncAdapter.issue(userId, catalog.getPrice() - transactionDTO.getDeduction(), catalog.getProvider().getId(), voucherId, methodId);

        } else if (response==HttpStatus.BAD_REQUEST){
          //increase balance back and unredeem voucher back persistently
          balance += catalog.getPrice()-transactionDTO.getDeduction();
          asyncAdapter.increaseBalance(userId,catalog.getPrice()-transactionDTO.getDeduction());
          asyncAdapter.unRedeem(userId,voucherId);
          //revert all transaction details but change to failed so that user won't try to pay again
          voucher = null;
          transactionDTO.setVoucherId(0);
          transactionDTO.setDeduction(0);
          transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.FAILED));
          transactionMapper.update(transactionDTO);
        } else {
          //change status to verifying
          transactionDTO.setStatusId(getIdByTransactionStatusName(TransactionStatusName.VERIFYING));
          transactionMapper.update(transactionDTO);
        }
        break;
    }

    //return details transaction, whether get voucher or not, and updated balance
    //if after all this process, failed to  get actual balance, the balance saved before would come in handy
    transactionDTO = transactionMapper.getById(transactionId);
    try {
      return new PayResponse(rpcService.getBalance(userId),isEligibleToGetVoucher,transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher));
    } catch (ServiceUnreachableException | OtherServiceException e) {
      return new PayResponse(balance,isEligibleToGetVoucher,transactionAdapter.transactionDTOtoTransactionAdapter(transactionDTO,voucher));
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
    if (page<1) throw new ServiceException(ResponseMessage.generic400);
    //call member domain, include return error if not found or error when send request
    validateUser(userId);

    //refresh transaction for this user
    refreshByUserId(userId);

    //calculate the offset
    long offset = (page-1)*10;
    //get the data by parameters
    List<TransactionDTO> transactionDTOS = transactionMapper.getAllByUserIdAndStatusTypeIdAndOffset(userId,getIdByTransactionStatusType(transactionStatusType),offset);

    //convert into accepted format, no validation for catalog etc since the data are from db and even
    //if the data soft deleted, it's still better to show since it's a history thing
    List<TransactionOverview> transactionOverview = new ArrayList<>();
    for (TransactionDTO transactionDTO:transactionDTOS)
      transactionOverview.add(transactionAdapter.transactionDTOtoTransactionOverviewAdapter(transactionDTO));

    //return, same as recent number, no error if empty just return empty array
    return transactionOverview;
  }

  ////////////////////////////////// VALIDATION ////////////////////////////////////
  private TransactionDTO getAndValidateTransactionDTO(long id) throws ServiceException {
    TransactionDTO transactionDTO = transactionMapper.getById(id);
    if (transactionDTO==null){
      throw new ServiceException(ResponseMessage.transaction404);
    }
    return transactionDTO;
  }

  private void validateUser(long userId) throws ServiceException {
    try {
      if (!rpcService.userExist(userId)) {
        throw new ServiceException(ResponseMessage.member404);
      }
    } catch (ServiceUnreachableException | OtherServiceException e) {
      throw new ServiceException(e.getMessage());
    }
  }

  private void validateTransactionBelongToUser(long userId, TransactionDTO transactionDTO) throws ServiceException {
    if (transactionDTO.getUserId()!=userId){
      throw new ServiceException(ResponseMessage.transaction404);
    }
  }

  private Voucher getAndValidateVoucher(long id) throws ServiceException {
    if (id==0) return null;
    try {
      return rpcService.getVoucher(id);
    } catch (ServiceUnreachableException e) {
      throw new ServiceException(e.getMessage());
    }
  }

  private void validatePhoneNumber(String phoneNumber) throws ServiceException {
    if (phoneNumber.length()<9||phoneNumber.length()>13||phoneNumber.charAt(0)!='0'){
      throw new ServiceException(ResponseMessage.phone400);
    }
    try {
      Long.parseLong(phoneNumber.substring(1));
    } catch (NumberFormatException e){
      throw new ServiceException(ResponseMessage.phone400);
    }
  }

  private Provider getAndValidateProvider(String phoneNumber) throws ServiceException {
    Provider provider = providerService.getProviderByPrefix(phoneNumber.substring(1,5));
    if (provider==null||provider.getDeletedAt()!=null){
      throw new ServiceException(ResponseMessage.phone404);
    }
    return provider;
  }

  private PulsaCatalogDTO getAndValidatePulsaCatalogDTO(long catalogId) throws ServiceException {
    PulsaCatalogDTO pulsaCatalogDTO = providerService.getCatalogDTObyId(catalogId);
    if (pulsaCatalogDTO==null||pulsaCatalogDTO.getDeletedAt()!=null) {
      throw new ServiceException(ResponseMessage.catalog404);
    }
    return pulsaCatalogDTO;
  }

  private void validateMethod(long methodId) throws ServiceException {
    PaymentMethodName paymentMethod = getPaymentMethodNameById(methodId);
    if (paymentMethod==null){
      throw new ServiceException(ResponseMessage.method404);
    }
  }

  private long getAndValidateEnoughBalance(long userId, long price, long deduction, long voucherId) throws ServiceException {
    try {
      long balance = rpcService.getBalance(userId);
      if (price-deduction>balance){
        if (voucherId!=0) asyncAdapter.unRedeem(userId, voucherId);
        throw new ServiceException(ResponseMessage.pay400);
      }
      return balance;
    } catch (ServiceUnreachableException | OtherServiceException e) {
      if (voucherId!=0) asyncAdapter.unRedeem(userId, voucherId);
      throw new ServiceException(e.getMessage());
    }
  }

  ///////////////////////////// OTHER BUSINESS FLOW ////////////////////////////////
  private Voucher redeemAndGetVoucherDetails(long userId, long methodId, long voucherId, long price, long providerId) throws ServiceException {
    //redeem voucher if using voucher
    //if redeeming process error, return the exact message because it's either error message from promotion or connection error
    Voucher voucher = null;
    if (voucherId!=0){
      Voucher redeemed;
      try {
        redeemed = rpcService.redeem(userId,voucherId,price,methodId,providerId);
      } catch (ServiceUnreachableException | OtherServiceException e){
        throw new ServiceException(e.getMessage()); //message from promotion
      }
      //if redeem success then get the details
      //if getting details failed, then unRedeem
      //because unRedeem is using persistent message, it'll stay until promotion domain up again
      try {
        voucher = rpcService.getVoucher(voucherId);
      } catch (ServiceUnreachableException e){
        asyncAdapter.unRedeem(userId,voucherId);
        throw new ServiceException(e.getMessage());
      }
      //save the details
      voucher.setValue(redeemed.getValue());
      voucher.setDeduction(price-redeemed.getFinalPrice());
    }
    return voucher;
  }

  private long decreaseBalance(long balance, long price, long deduction, long userId, long voucherId) throws ServiceException {
    try {
      rpcService.decreaseBalance(userId,price-deduction);
      return balance - (price-deduction);
    } catch (ServiceUnreachableException | OtherServiceException e) {
      if (voucherId!=0) asyncAdapter.unRedeem(userId, voucherId);
      throw new ServiceException(e.getMessage());
    }
  }

  ////////////////////////////////// HELPER ////////////////////////////////////
  @Transactional(propagation = Propagation.NESTED)
  void refreshByUserId(long userId) {
    transactionMapper.refreshStatus(userId, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
  }

  @Transactional(propagation = Propagation.NESTED)
  void refreshById(long id) {
    transactionMapper.refreshStatusById(id, Global.TRANSACTION_LIFETIME_HOURS,
        getIdByTransactionStatusName(TransactionStatusName.EXPIRED), getIdByTransactionStatusName(TransactionStatusName.WAITING));
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
}