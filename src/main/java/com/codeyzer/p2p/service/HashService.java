package com.codeyzer.p2p.service;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HashService {

    @Value("${file-share.hash-length:4}")
    private int hashLength;
    
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
        return RandomStringUtils.random(hashLength, 0, 0, true, true, null, secureRandom);
    }
    
    /**
     * Hızlı bir hash değeri oluşturur (güvenlik gerektirmeyen durumlarda)
     * @return Rastgele oluşturulmuş alfanumerik bir string
     */
    public String generateFastHash() {
        return RandomStringUtils.random(hashLength, 0, 0, true, true, null, ThreadLocalRandom.current());
    }
}
