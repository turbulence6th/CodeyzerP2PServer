package com.codeyzer.p2p.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareHeartbeatEntry {
    private String shareHash;
    private String ownerToken;
} 