package com.debrief2.pulsa.order.controller;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.payload.request.CreateTransactionRequest;
import com.debrief2.pulsa.order.payload.request.TesterRequest;
import com.debrief2.pulsa.order.payload.request.TransactionHistoryRequest;
import com.debrief2.pulsa.order.payload.request.TransactionRequest;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/api/test")
public class TestController {

  @Autowired
  private TransactionService transactionService;
  @Autowired
  private ProviderService providerService;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @PostMapping("/")
  public ResponseEntity<?> forTester(@RequestBody TesterRequest testerRequest){
    String message = testerRequest.getMessage();
    String response = "";
    try {
      switch (testerRequest.getMethod()){
        case "getAllCatalog":
          AllPulsaCatalogResponse pulsaCatalogResponses = providerService.getAllCatalog(message);
          response = objectMapper.writeValueAsString(pulsaCatalogResponses);
          break;
        case "getRecentNumber":
          List<RecentNumberResponse> recentNumbers = transactionService.getRecentNumber(Long.parseLong(message));
          response = objectMapper.writeValueAsString(recentNumbers);
          break;
        case "cancel":
          TransactionRequest request = objectMapper.readValue(message, TransactionRequest.class);
          TransactionResponseNoVoucher transaction = transactionService.cancel(request.getUserId(), request.getTransactionId());
          response = objectMapper.writeValueAsString(transaction);
          break;
        case "getProviderById":
          Provider provider = providerService.getProviderById(Long.parseLong(message));
          response = objectMapper.writeValueAsString(provider);
          if (provider==null||provider.getDeletedAt()!=null) {
            response = ResponseMessage.getProviderById404;
          }
          break;
        case "getPaymentMethodNameById":
          PaymentMethodName paymentMethodName = transactionService.getPaymentMethodNameById(Long.parseLong(message));
          response = objectMapper.writeValueAsString(paymentMethodName);
          if (paymentMethodName==null){
            response = ResponseMessage.getPaymentMethodNameById404;
          }
          break;
        case "getTransactionById":
          TransactionResponseWithMethodId transactionResponse = transactionService.getTransactionById(Long.parseLong(message));
          response = objectMapper.writeValueAsString(transactionResponse);
          break;
        case "getTransactionByIdByUserId":
          TransactionRequest request2 = objectMapper.readValue(message, TransactionRequest.class);
          TransactionResponse transactionResponse2 = transactionService.getTransactionByIdByUserId(request2.getTransactionId(), request2.getUserId());
          response = objectMapper.writeValueAsString(transactionResponse2);
          break;
        case "getHistoryInProgress":
          TransactionHistoryRequest historyRequest = objectMapper.readValue(message, TransactionHistoryRequest.class);
          List<TransactionOverviewResponse> overviewResponses = transactionService.getHistoryInProgress(historyRequest.getUserId(),historyRequest.getPage());
          response = objectMapper.writeValueAsString(overviewResponses);
          break;
        case "getHistoryCompleted":
          TransactionHistoryRequest historyRequest2 = objectMapper.readValue(message, TransactionHistoryRequest.class);
          List<TransactionOverviewResponse> overviewResponses2 = transactionService.getHistoryCompleted(historyRequest2.getUserId(),historyRequest2.getPage());
          response = objectMapper.writeValueAsString(overviewResponses2);
          break;
        case "createTransaction":
          CreateTransactionRequest createTransactionRequest = objectMapper.readValue(message, CreateTransactionRequest.class);
          OrderResponse orderResponse = transactionService.createTransaction(createTransactionRequest.getUserId(),createTransactionRequest.getCatalogId(),createTransactionRequest.getPhoneNumber());
          response = objectMapper.writeValueAsString(orderResponse);
          break;
        case "pay":
          TransactionRequest payRequest = objectMapper.readValue(message, TransactionRequest.class);
          PayResponse payResponse = transactionService.pay(payRequest.getUserId(),payRequest.getTransactionId(),payRequest.getMethodId(),payRequest.getVoucherId());
          response = objectMapper.writeValueAsString(payResponse);
          break;
        default:
          response = "unknown service method";
          break;
      }
    } catch (ServiceException serviceException) {
      response = serviceException.getMessage();
    } catch (NumberFormatException | JsonProcessingException e) {
      response = ResponseMessage.generic400;
    }
    return new ResponseEntity<>(response,HttpStatus.OK);
  }
}
