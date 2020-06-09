package com.debrief2.pulsa.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncAdapter {
  @Autowired
  ProviderService providerService;

  private static final Logger log = LoggerFactory.getLogger(AsyncAdapter.class);

  public CompletableFuture<Void>reloadPrefix(){
    log.info("reloading prefix started");
    providerService.reloadPrefix();
    log.info("reloading prefix finished");
    return CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<Void>reloadCatalog(){
    log.info("reloading catalog started");
    providerService.reloadCatalog();
    log.info("reloading catalog finished");
    return CompletableFuture.completedFuture(null);
  }
}
