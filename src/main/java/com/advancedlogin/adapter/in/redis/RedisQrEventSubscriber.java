package com.advancedlogin.adapter.in.redis;

import com.advancedlogin.adapter.common.redis.QrEventChannel;
import com.advancedlogin.adapter.in.web.qr.QrStatusStreams;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Forwards Pub/Sub updates (published by any instance) to the SSE streams held by this one. */
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
