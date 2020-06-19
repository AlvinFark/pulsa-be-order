package com.debrief2.pulsa.order.service.impl;

import com.debrief2.pulsa.order.exception.OtherServiceException;
import com.debrief2.pulsa.order.exception.ServiceUnreachableException;
import com.debrief2.pulsa.order.model.PulsaCatalog;
import com.debrief2.pulsa.order.model.Voucher;
import com.debrief2.pulsa.order.payload.request.BalanceRequest;
import com.debrief2.pulsa.order.payload.request.IssueVoucherRequest;
import com.debrief2.pulsa.order.payload.request.RedeemRequest;
import com.debrief2.pulsa.order.payload.request.UnRedeemRequest;
import com.debrief2.pulsa.order.service.RPCService;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import com.debrief2.pulsa.order.utils.rpc.RPCClient;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

@Service
public class RPCServiceImpl implements RPCService {

  @Autowired
  RPCClient rpcClient;

  private final String promotionUrl = "amqp://ynjauqav:K83KvUARdw7DyYLJF2_gt2RVzO-NS2YM@lively-peacock.rmq.cloudamqp.com/ynjauqav";
  private final String memberUrl = "amqp://ynjauqav:K83KvUARdw7DyYLJF2_gt2RVzO-NS2YM@lively-peacock.rmq.cloudamqp.com/ynjauqav";
  final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public boolean userExist(long id) throws ServiceUnreachableException, OtherServiceException {
    String response = "";
    try {
      response = rpcClient.call(memberUrl,"getBalance",String.valueOf(id));
      Long.parseLong(response.substring(1, response.length() - 1));
      return true;
    } catch (NumberFormatException e) {
      if (response.equals("user not found")){
        return false;
      }
      throw new OtherServiceException(response);
    } catch (IOException | TimeoutException | URISyntaxException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    }
  }

  @Override
  public long getBalance(long id) throws ServiceUnreachableException, OtherServiceException {
    String response = null;
    try {
      response = rpcClient.call(memberUrl, "getBalance", String.valueOf(id));
      return Long.parseLong(response.substring(1, response.length() - 1));
    } catch (NumberFormatException e) {
      throw new OtherServiceException(response);
    } catch (IOException | TimeoutException | URISyntaxException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    }
  }

  @Override
  public void decreaseBalance(long userId, long value) throws ServiceUnreachableException, OtherServiceException {
    try {
      BalanceRequest request = new BalanceRequest(userId,value);
      String message = rpcClient.call(memberUrl,"decreaseBalance",objectMapper.writeValueAsString(request));
      if (!(message.equals("\"success\"")||message.equals("success"))){
        throw new OtherServiceException(message);
      }
    } catch (IOException | TimeoutException | URISyntaxException e) {
      throw new ServiceUnreachableException(ResponseMessage.memberConnection);
    }
  }

  @Override
  public void increaseBalance(long userId, long value) {
    BalanceRequest request = new BalanceRequest(userId,value);
    try {
      rpcClient.persistentCall(memberUrl,"increaseBalance",objectMapper.writeValueAsString(request));
    } catch (JsonProcessingException ignored) {}
  }

  @Override
  public boolean eligibleToGetVoucher(long userId, long price, long providerId, long voucherId, long paymentMethodId) throws ServiceUnreachableException {
    try {
      IssueVoucherRequest request = new IssueVoucherRequest(userId, price, providerId, voucherId, paymentMethodId);
      return objectMapper.readValue(rpcClient.call(promotionUrl,"eligibleToGetVoucher",objectMapper.writeValueAsString(request)),Boolean.class);
    } catch (IOException | TimeoutException | URISyntaxException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    }
  }

  @Override
  public Voucher redeem(long userId, long voucherId, long price, long paymentMethodId, long providerId) throws ServiceUnreachableException, OtherServiceException {
    String message = "";
    try {
      RedeemRequest request = new RedeemRequest(userId,voucherId,price,paymentMethodId,providerId);
      message = rpcClient.call(promotionUrl,"redeem",objectMapper.writeValueAsString(request));
      return objectMapper.readValue(message,Voucher.class);
    } catch (JsonParseException e) {
      throw new OtherServiceException(message);
    } catch (IOException | TimeoutException | URISyntaxException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    }
  }

  @Override
  public void unRedeem(long userId, long voucherId) {
    UnRedeemRequest request = new UnRedeemRequest(userId,voucherId);
    try {
      rpcClient.persistentCall(promotionUrl,"unredeem",objectMapper.writeValueAsString(request));
    } catch (JsonProcessingException ignored) {}
  }

  @Override
  public void issue(long userId, long price, long providerId, long voucherId, long paymentMethodId) {
    IssueVoucherRequest request = new IssueVoucherRequest(userId, price, providerId, voucherId, paymentMethodId);
    try {
      rpcClient.persistentCall(promotionUrl,"issue",objectMapper.writeValueAsString(request));
    } catch (JsonProcessingException ignored) {}
  }

  @Override
  public Voucher getVoucher(long id) throws ServiceUnreachableException {
    String message;
    try {
      message = rpcClient.call(promotionUrl,"getVoucherDetail",String.valueOf(id));
      return objectMapper.readValue(message,Voucher.class);
    } catch (JsonParseException e) {
      return null;
    } catch (IOException | TimeoutException | URISyntaxException e) {
      throw new ServiceUnreachableException(ResponseMessage.promotionConnection);
    }
  }

  //third party call
  @Override
  public HttpStatus sendTopUpRequestTo3rdPartyServer(String phoneNumber, PulsaCatalog catalog){
    return HttpStatus.ACCEPTED;
//    return HttpStatus.BAD_REQUEST;
//    return HttpStatus.INTERNAL_SERVER_ERROR;
  }
}
