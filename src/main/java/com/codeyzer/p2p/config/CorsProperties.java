package com.codeyzer.p2p.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class CorsProperties {
    
    private CorsSetting cors = new CorsSetting();
    private WebSocketSetting websocket = new WebSocketSetting();
    
} 