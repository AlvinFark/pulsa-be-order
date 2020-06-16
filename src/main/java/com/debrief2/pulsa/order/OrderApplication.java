package com.debrief2.pulsa.order;

import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.utils.rpc.RPCServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderApplication implements CommandLineRunner {

  @Autowired
  TransactionService transactionService;
  @Autowired
  RPCServer rpcServer;

  @Value("${cloudAMQP.url}")
  private String url;

  public static void main(String[] args) {
    SpringApplication.run(OrderApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
//    rpcServer.run("getAllCatalog");
    rpcServer.run("getAllCatalog");
//    rpcServer.run("getRecentNumber");
    rpcServer.run("getRecentNumber");
//    rpcServer.run("cancel");
    rpcServer.run("cancel");
//    rpcServer.run("getProviderById");
    rpcServer.run("getProviderById");
//    rpcServer.run("getPaymentMethodNameById");
    rpcServer.run("getPaymentMethodNameById");
//    rpcServer.run("getTransactionById");
    rpcServer.run("getTransactionById");
//    rpcServer.run("getTransactionByIdByUserId");
    rpcServer.run("getTransactionByIdByUserId");
//    rpcServer.run("getHistoryInProgress");
    rpcServer.run("getHistoryInProgress");
//    rpcServer.run("getHistoryCompleted");
    rpcServer.run("getHistoryCompleted");
//    rpcServer.run("createTransaction");
    rpcServer.run("createTransaction");
//    rpcServer.run("pay");
//    rpcServer.run("pay");
    rpcServer.run("pay");
  }
}
