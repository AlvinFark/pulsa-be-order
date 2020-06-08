package com.debrief2.pulsa.order;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.service.OrderService;
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
  OrderService orderService;
  @Autowired
  RPCServer rpcServer;

  @Value("${cloudAMQP.url}")
  private String url;

  public static void main(String[] args) {
    SpringApplication.run(OrderApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    rpcServer.run("getAllCatalog");
    rpcServer.run("getRecentNumber");
    rpcServer.run("cancel");

    Thread.sleep(5000);
    RPCClient rpcClient = new RPCClient(url,"getAllCatalog");
    System.out.println(rpcClient.call("08520"));
    RPCClient rpcClient2 = new RPCClient(url,"getRecentNumber");
    System.out.println(rpcClient2.call("1"));
    RPCClient rpcClient3 = new RPCClient(url,"cancel");
    System.out.println(rpcClient3.call("{\"userId\":1,\"transactionId\":1}"));
  }
}
