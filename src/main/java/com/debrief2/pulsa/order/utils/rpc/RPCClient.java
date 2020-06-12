package com.debrief2.pulsa.order.utils.rpc;

import com.rabbitmq.client.*;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Data
public class RPCClient {
  public static String call(String url, String routingKey, String message) throws IOException, TimeoutException, URISyntaxException {
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
    params.timeout(10000);
    return new RpcClient(params).stringCall(message);
  }

  public static void persistentCall(String url, String routingKey, String message) throws URISyntaxException, IOException, TimeoutException {
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
    channel.basicPublish("", routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.UTF_8));
  }
}
