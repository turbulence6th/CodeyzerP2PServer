package com.codeyzer.p2p.config;

import com.codeyzer.p2p.dto.FileShareWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class ShareConfig {

    @Bean
    public Map<String, FileShareWrapper> shareMap() {
        return new ConcurrentHashMap<>();
    }
}
