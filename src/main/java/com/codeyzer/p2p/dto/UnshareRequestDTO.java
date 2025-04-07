package com.codeyzer.p2p.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnshareRequestDTO {

    private String shareHash;
}
