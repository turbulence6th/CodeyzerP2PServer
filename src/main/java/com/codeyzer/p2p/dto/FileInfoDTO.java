package com.codeyzer.p2p.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoDTO {
    private String fileName;
    private long fileSize;
    private String fileType;
    // İsteğe bağlı olarak başka alanlar da eklenebilir
} 