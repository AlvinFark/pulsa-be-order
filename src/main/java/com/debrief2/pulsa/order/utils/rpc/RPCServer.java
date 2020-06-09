package com.debrief2.pulsa.order.utils.rpc;

import com.debrief2.pulsa.order.exception.ServiceException;
import com.debrief2.pulsa.order.model.Provider;
import com.debrief2.pulsa.order.model.enums.PaymentMethodName;
import com.debrief2.pulsa.order.payload.request.TransactionRequest;
import com.debrief2.pulsa.order.payload.response.*;
import com.debrief2.pulsa.order.service.TransactionService;
import com.debrief2.pulsa.order.service.ProviderService;
import com.debrief2.pulsa.order.utils.ResponseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RPCServer {
  @Autowired
  private TransactionService transactionService;
  @Autowired
  private ProviderService providerService;

  private static final Logger log = LoggerFactory.getLogger(RpcServer.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${cloudAMQP.url}")
  private String url;

  @Async("workerExecutor")
  public void run(String queueName) throws URISyntaxException {
    final URI rabbitMqUrl = new URI(url);

    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername(rabbitMqUrl.getUserInfo().split(":")[0]);
    factory.setPassword(rabbitMqUrl.getUserInfo().split(":")[1]);
    factory.setHost(rabbitMqUrl.getHost());
    factory.setPort(rabbitMqUrl.getPort());
    factory.setVirtualHost(rabbitMqUrl.getPath().substring(1));

    try (Connection connection = factory.newConnection();
         Channel channel = connection.createChannel()) {
      channel.queueDeclare(queueName, true, false, false, null);
      channel.queuePurge(queueName);
      channel.basicQos(1);
      log.info("["+queueName+"] Awaiting requests");

      Object monitor = new Object();
      DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        AMQP.BasicProperties replyProps = new AMQP.BasicProperties
            .Builder()
            .correlationId(delivery.getProperties().getCorrelationId())
            .build();

        String response = "";
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        log.info("["+queueName+"] receiving request for "+message);

        try {
          switch (queueName){
            case "getAllCatalog":
              AllPulsaCatalogResponse pulsaCatalogResponses = transactionService.getAllCatalog(message);
              response = objectMapper.writeValueAsString(pulsaCatalogResponses);
              break;
            case "getRecentNumber":
              try {
                List<RecentNumberResponse> recentNumbers = transactionService.getRecentNumber(Long.parseLong(message));
                response = objectMapper.writeValueAsString(recentNumbers);
              } catch (NumberFormatException numberFormatException) {
                response = ResponseMessage.getRecentNumber400;
              }
              break;
            case "cancel":
              TransactionRequest request = objectMapper.readValue(message, TransactionRequest.class);
              TransactionResponseNoVoucher transaction = transactionService.cancel(request.getUserId(), request.getTransactionId());
              response = objectMapper.writeValueAsString(transaction);
              break;
            case "getProviderById":
              try {
                Provider provider = providerService.getProviderById(Long.parseLong(message));
                if (provider.getDeletedAt() != null) {
                  provider = null;
                }
                response = objectMapper.writeValueAsString(provider);
              } catch (NumberFormatException numberFormatException) {
                response = ResponseMessage.getProviderById400;
              }
              break;
            case "getPaymentMethodNameById":
              try {
                PaymentMethodName paymentMethodName = transactionService.getPaymentMethodNameById(Long.parseLong(message));
                response = objectMapper.writeValueAsString(paymentMethodName);
              } catch (NumberFormatException numberFormatException) {
                response = ResponseMessage.getPaymentMethodNameById400;
              }
              break;
            default:
              response = "Unknown service method";
              break;
          }
        } catch (ServiceException serviceException) {
          response = serviceException.getMessage();
        } catch (InvalidFormatException invalidFormatException) {
          response = ResponseMessage.generic400;
        }
        channel.basicPublish("", delivery.getProperties().getReplyTo(), replyProps, response.getBytes(StandardCharsets.UTF_8));
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        synchronized (monitor) {
          monitor.notify();
        }
      };

      channel.basicConsume(queueName, false, deliverCallback, (consumerTag -> { }));
      while (true) {
        synchronized (monitor) {
          try {
            monitor.wait();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
