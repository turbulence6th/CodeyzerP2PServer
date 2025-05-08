package com.codeyzer.p2p.dto;

import lombok.Data;
import java.util.List;

@Data // Getter, Setter, ToString, EqualsAndHashCode, RequiredArgsConstructor
public class HeartbeatPayload {
    // "type" alanı WebSocket mesajlaşmasında genellikle ayrıştırma için kullanılır,
    // ancak Spring @MessageMapping bunu doğrudan payload tipine göre yapabilir.
    // Eğer mesajda "type": "ACTIVE_SHARES_HEARTBEAT" gibi bir alan varsa ve 
    // bunu da almak istiyorsanız, buraya 'private String type;' ekleyebilirsiniz.
    private List<ShareHeartbeatEntry> shares;
} 