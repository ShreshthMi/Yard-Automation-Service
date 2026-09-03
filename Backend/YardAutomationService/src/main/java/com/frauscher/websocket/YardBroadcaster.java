package com.frauscher.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.frauscher.notification.YardMessage;

/** Publishes one yard's message to /topic/yard/{yardName}. */
@Component
public class YardBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public YardBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(YardMessage message) {
        messagingTemplate.convertAndSend("/topic/yard/" + message.yardName(), message);
    }
}
