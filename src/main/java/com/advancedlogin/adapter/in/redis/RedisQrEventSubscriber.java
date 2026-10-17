package com.advancedlogin.adapter.in.redis;

import com.advancedlogin.adapter.common.redis.QrEventChannel;
import com.advancedlogin.adapter.in.web.qr.QrStatusStreams;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Inbound adapter: a message arriving from Redis is an event from the outside world, just like
 * an HTTP request. Every backend instance runs one of these (subscribed to {@code qr:events:*},
 * see config/RedisMessagingConfig) and forwards updates to the SSE streams it holds.
 */
@Component
public class RedisQrEventSubscriber implements MessageListener {

    private final QrStatusStreams streams;

    RedisQrEventSubscriber(QrStatusStreams streams) {
        this.streams = streams;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        QrEventChannel.decode(channel, payload).ifPresent(streams::deliver);
    }
}
