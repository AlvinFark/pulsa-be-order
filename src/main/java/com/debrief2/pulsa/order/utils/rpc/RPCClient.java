package com.debrief2.pulsa.order.utils.rpc;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Component
public class RPCClient {

  private static final Logger log = LoggerFactory.getLogger(RpcServer.class);

  public String call(String url, String routingKey, String message) throws IOException, TimeoutException, URISyntaxException {
    final URI rabbitMqUrl = new URI(url);
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername(rabbitMqUrl.getUserInfo().split(":")[0]);
    factory.setPassword(rabbitMqUrl.getUserInfo().split(":")[1]);
    factory.setHost(rabbitMqUrl.getHost());
    factory.setPort(rabbitMqUrl.getPort());
    factory.setVirtualHost(rabbitMqUrl.getPath().substring(1));
    Connection connection = factory.newConnection();
    RpcClientParams params = new RpcClientParams();
    params.channel(connection.createChannel());
    params.exchange("");
    params.routingKey(routingKey);
    params.timeout(15000);
    RpcClient rpcClient = new RpcClient(params);
    String response  = rpcClient.stringCall(message);
    rpcClient.close();
    return response;
  }

  public void persistentCall(String url, String routingKey, String message) {
    try {
      final URI rabbitMqUrl = new URI(url);
      ConnectionFactory factory = new ConnectionFactory();
      factory.setUsername(rabbitMqUrl.getUserInfo().split(":")[0]);
      factory.setPassword(rabbitMqUrl.getUserInfo().split(":")[1]);
      factory.setHost(rabbitMqUrl.getHost());
      factory.setPort(rabbitMqUrl.getPort());
      factory.setVirtualHost(rabbitMqUrl.getPath().substring(1));
      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(routingKey, true, false, false, null);
      channel.basicPublish("", routingKey,MessageProperties.PERSISTENT_TEXT_PLAIN,message.getBytes(StandardCharsets.UTF_8));
      log.info(message+" sent to "+routingKey);
    } catch (URISyntaxException | IOException | TimeoutException e) {
      e.printStackTrace();
    }
  }
}
