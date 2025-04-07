package com.codeyzer.p2p.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileShareWrapper {

    private String filename;
    private Long size;
    private Map<String, FileStreamWrapper> streamMap;
}
