package com.debrief2.pulsa.order.utils.rpc;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
@NoArgsConstructor
public class RPCClient {
  private Connection connection;
  private Channel channel;
  private String queueName;

  public RPCClient(String url, String queueName) throws Exception {
    final URI rabbitMqUrl = new URI(url);

    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername(rabbitMqUrl.getUserInfo().split(":")[0]);
    factory.setPassword(rabbitMqUrl.getUserInfo().split(":")[1]);
    factory.setHost(rabbitMqUrl.getHost());
    factory.setPort(rabbitMqUrl.getPort());
    factory.setVirtualHost(rabbitMqUrl.getPath().substring(1));

    this.queueName = queueName;
    this.connection = factory.newConnection();
    this.channel = connection.createChannel();
  }

  public String call(String message) throws IOException, InterruptedException {
    final String corrId = UUID.randomUUID().toString();

    String replyQueueName = channel.queueDeclare().getQueue();
    AMQP.BasicProperties props = new AMQP.BasicProperties
        .Builder()
        .correlationId(corrId)
        .replyTo(replyQueueName)
        .build();

    channel.basicPublish("", queueName, props, message.getBytes(StandardCharsets.UTF_8));

    final BlockingQueue<String> response = new ArrayBlockingQueue<>(1);

    String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
      if (delivery.getProperties().getCorrelationId().equals(corrId)) {
        response.offer(new String(delivery.getBody(), StandardCharsets.UTF_8));
      }
    }, consumerTag -> {
    });

    String result = response.take();
    channel.basicCancel(ctag);
    return result;
  }

  public void close() throws IOException {
    connection.close();
  }
}
