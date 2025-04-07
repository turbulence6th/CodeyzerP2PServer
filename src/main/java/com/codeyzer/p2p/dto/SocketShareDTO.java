package com.codeyzer.p2p.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocketShareDTO {

    private String shareHash;
    private String streamHash;
    private String ip;
}
