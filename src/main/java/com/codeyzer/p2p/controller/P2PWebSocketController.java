package com.codeyzer.p2p.controller;

import com.codeyzer.p2p.dto.HeartbeatPayload;
import com.codeyzer.p2p.dto.ShareHeartbeatEntry;
import com.codeyzer.p2p.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class P2PWebSocketController {

    private final FileService fileService;

    /**
     * İstemciden gelen aktif paylaşım kalp atışlarını (WebSocket) işler.
     * @param payload Kalp atışı payload'ı, aktif shareHash ve ownerToken listesini içerir.
     */
    @MessageMapping("/p2p/shares/heartbeat") // İstemcinin mesaj göndereceği adres (örn: /app/p2p/shares/heartbeat)
    public void handleShareHeartbeat(@Payload HeartbeatPayload payload) {
        if (payload == null || payload.getShares() == null || payload.getShares().isEmpty()) {
            log.warn("Received an empty or invalid heartbeat payload via WebSocket.");
            return;
        }

        log.debug("Received WebSocket heartbeat for {} shares.", payload.getShares().size());
        for (ShareHeartbeatEntry entry : payload.getShares()) {
            if (entry != null && entry.getShareHash() != null && !entry.getShareHash().trim().isEmpty()
                && entry.getOwnerToken() != null && !entry.getOwnerToken().trim().isEmpty()) 
            {
                // FileService'deki updateHeartbeat zaten ownerToken kontrolünü yapıyor
                fileService.updateHeartbeat(entry.getShareHash(), entry.getOwnerToken());
            }
        }
    }
    
    // Gelecekte başka P2P WebSocket mesajları için handler'lar buraya eklenebilir.
} 