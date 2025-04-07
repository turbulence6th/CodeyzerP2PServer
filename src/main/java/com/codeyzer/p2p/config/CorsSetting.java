package com.codeyzer.p2p.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CorsSetting {
    private List<String> allowedOrigins = new ArrayList<>();
} 