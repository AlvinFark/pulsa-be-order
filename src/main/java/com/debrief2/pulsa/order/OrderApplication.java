package com.debrief2.pulsa.order;

import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.utils.rpc.RPCClient;
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
//    rpcServer.run("getRecentNumber");
//    rpcServer.run("cancel");
//    rpcServer.run("getProviderById");
//    rpcServer.run("getPaymentMethodNameById");
    rpcServer.run("getTransactionById");
//    rpcServer.run("getTransactionByIdByUserId");
//    rpcServer.run("getHistoryInProgress");
//    rpcServer.run("getHistoryCompleted");
//    rpcServer.run("createTransaction");

    Thread.sleep(1000);
//    RPCClient rpcClient = new RPCClient(url,"getAllCatalog");
//    System.out.println(rpcClient.call("08520"));
//    RPCClient rpcClient2 = new RPCClient(url,"getRecentNumber");
//    System.out.println(rpcClient2.call("1A"));
//    RPCClient rpcClient3 = new RPCClient(url,"cancel");
//    System.out.println(rpcClient3.call("{\"userId\":\"1A\",\"transactionId\":\"1\"}"));
//    RPCClient rpcClient4 = new RPCClient(url,"getProviderById");
//    System.out.println(rpcClient4.call("10"));
//    RPCClient rpcClient5 = new RPCClient(url,"getPaymentMethodNameById");
//    System.out.println(rpcClient5.call("10"));
    RPCClient rpcClient6 = new RPCClient(url,"getTransactionById");
    System.out.println(rpcClient6.call("1"));
//    RPCClient rpcClient7 = new RPCClient(url,"getTransactionByIdByUserId");
//    System.out.println(rpcClient7.call("{\"userId\":\"1\",\"transactionId\":\"1\"}"));
//    RPCClient rpcClient8 = new RPCClient(url,"getHistoryInProgress");
//    System.out.println(rpcClient8.call("{\"userId\":\"1\",\"page\":\"1\"}"));
//    RPCClient rpcClient9 = new RPCClient(url,"getHistoryCompleted");
//    System.out.println(rpcClient9.call("{\"userId\":\"1\",\"page\":\"1\"}"));
//    RPCClient rpcClient10 = new RPCClient(url,"createTransaction");
//    System.out.println(rpcClient10.call("{\"userId\":1,\"phoneNumber\":\"085200000000\",\"catalogId\":15}"));
  }
}
