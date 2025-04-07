package com.codeyzer.p2p.config;

import com.codeyzer.p2p.dto.FileShareWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ShareConfig {

    @Bean
    public Map<String, FileShareWrapper> shareMap() {
        return new HashMap<>();
    }
}
