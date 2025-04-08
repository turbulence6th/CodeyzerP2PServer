package com.codeyzer.p2p.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codeyzer.p2p.dto.FileShareWrapper;
import com.codeyzer.p2p.dto.UnshareRequestDTO;
import com.codeyzer.p2p.service.monitoring.PerformanceMonitorService;
import com.codeyzer.p2p.service.monitoring.TransferMetric;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "file-share.cleanup.status", name = "enabled")
public class CleanupService {

    private final Map<String, FileShareWrapper> shareMap;
    private final FileService fileService;
    private final PerformanceMonitorService monitorService;
    
    @Value("${file-share.cleanup.inactive-timeout-minutes:60}")
    private int inactiveTimeoutMinutes;
    
    @Value("${file-share.cleanup.max-age-hours:24}")
    private int maxAgeHours;
    
    /**
     * Kullanılmayan paylaşımları temizle
     * Her 15 dakikada bir çalışır
     */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void cleanupInactiveShares() {
        log.info("Kullanılmayan paylaşımları temizleme işlemi başlatılıyor...");
        
        Instant now = Instant.now();
        List<String> hashesToRemove = new ArrayList<>();
        
        // Belirli bir süre inaktif olan veya belirli bir yaşı geçmiş paylaşımları bul
        shareMap.forEach((hash, shareWrapper) -> {
            TransferMetric metric = monitorService.getMetric(hash).orElse(null);
            
            if (metric != null) {
                // Son kullanım zamanı
                Instant lastUsed = metric.getLastUsed();
                // Oluşturulma zamanı
                Instant createdAt = metric.getCreatedAt();
                
                // İnaktivite durumu
                boolean isInactive = Duration.between(lastUsed, now).toMinutes() > inactiveTimeoutMinutes;
                // Yaş durumu
                boolean isTooOld = Duration.between(createdAt, now).toHours() > maxAgeHours;
                
                // Stream yok ve inaktif veya çok eski
                if (shareWrapper.getStreamMap().isEmpty() && (isInactive || isTooOld)) {
                    if (isInactive) {
                        log.info("Paylaşım inaktif: {} - Son kullanım: {}", hash, lastUsed);
                    }
                    if (isTooOld) {
                        log.info("Paylaşım çok eski: {} - Oluşturulma: {}", hash, createdAt);
                    }
                    hashesToRemove.add(hash);
                }
            } else {
                // Metrik yok, muhtemelen temizlenmesi gerekiyor
                log.info("Metrik olmayan paylaşım: {}", hash);
                hashesToRemove.add(hash);
            }
        });
        
        // Temizlenecek paylaşımları kaldır
        for (String hash : hashesToRemove) {
            try {
                log.info("Paylaşım temizleniyor: {}", hash);
                fileService.unshare(UnshareRequestDTO.builder().shareHash(hash).build());
            } catch (Exception e) {
                log.error("Paylaşım temizlenirken hata: {}", hash, e);
            }
        }
        
        log.info("Kullanılmayan paylaşımları temizleme işlemi tamamlandı. Temizlenen: {}", hashesToRemove.size());
    }
} 