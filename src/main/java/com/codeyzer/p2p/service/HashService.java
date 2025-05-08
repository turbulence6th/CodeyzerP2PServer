package com.codeyzer.p2p.service;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

import com.codeyzer.p2p.config.FileShareProperties;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HashService {

    private final FileShareProperties fileShareProperties;
    
    private SecureRandom secureRandom;
    
    @PostConstruct
    public void init() {
        // Daha güvenli rastgele sayı üretici
        secureRandom = new SecureRandom();
        secureRandom.setSeed(System.currentTimeMillis());
    }

    /**
     * Benzersiz bir hash değeri oluşturur
     * @return Rastgele oluşturulmuş alfanumerik bir string
     */
    public String generateHash() {
        // Daha güvenli rastgele değer oluşturma
        return RandomStringUtils.random(fileShareProperties.getHashLength(), 0, 0, true, true, null, secureRandom);
    }
    
    /**
     * Hızlı bir hash değeri oluşturur (güvenlik gerektirmeyen durumlarda)
     * @return Rastgele oluşturulmuş alfanumerik bir string
     */
    public String generateFastHash() {
        return RandomStringUtils.random(fileShareProperties.getHashLength(), 0, 0, true, true, null, ThreadLocalRandom.current());
    }
}
