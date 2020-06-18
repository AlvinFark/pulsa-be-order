package com.debrief2.pulsa.order.service;

import com.debrief2.pulsa.order.exception.OtherServiceException;
import com.debrief2.pulsa.order.exception.ServiceUnreachableException;
import com.debrief2.pulsa.order.payload.dto.TransactionDTO;
import com.debrief2.pulsa.order.repository.TransactionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncAdapter {
  @Autowired
  ProviderService providerService;
  @Autowired
  RPCService rpcService;
  @Autowired
  TransactionMapper transactionMapper;

  private static final Logger log = LoggerFactory.getLogger(AsyncAdapter.class);

  @Async("asyncExecutor")
  public CompletableFuture<Void> reloadPrefix(){
    log.info("reloading prefix started");
    providerService.reloadPrefix();
    log.info("reloading prefix finished");
    return CompletableFuture.completedFuture(null);
  }

  @Async("asyncExecutor")
  public CompletableFuture<Void> reloadCatalog(){
    log.info("reloading catalog started");
    providerService.reloadCatalog();
    log.info("reloading catalog finished");
    return CompletableFuture.completedFuture(null);
  }

  @Async("asyncExecutor")
  public CompletableFuture<Void> increaseBalance(long userId, long value) {
    log.info("increasing balance started");
    rpcService.increaseBalance(userId,value);
    log.info("increasing balance finished");
    return CompletableFuture.completedFuture(null);
  }

  @Async("asyncExecutor")
  public CompletableFuture<Void> issue(long userId, long price, long providerId, long voucherId, long paymentMethodId) {
    log.info("issuing voucher started");
    rpcService.issue(userId,price,providerId,voucherId,paymentMethodId);
    log.info("issuing voucher finished");
    return CompletableFuture.completedFuture(null);
  }

  @Async("asyncExecutor")
  public CompletableFuture<Void> unRedeem(long userId, long voucherId) {
    log.info("unRedeeming started");
    rpcService.unRedeem(userId, voucherId);
    log.info("unRedeeming finished");
    return CompletableFuture.completedFuture(null);
  }
}
