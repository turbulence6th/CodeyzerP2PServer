package com.codeyzer.p2p.service.monitoring;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dosya paylaşım işlemlerinin performansını izleyen ve raporlayan servis.
 * Bu servis, dosya indirme ve yükleme işlemlerinin istatistiklerini toplar ve periyodik olarak raporlar.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PerformanceMonitorService {

    @Getter
    private AtomicLong totalUploads = new AtomicLong(0);
    
    @Getter
    private AtomicLong totalDownloads = new AtomicLong(0);
    
    @Getter
    private AtomicLong totalBytesTransferred = new AtomicLong(0);
    
    private Map<String, TransferMetric> transferMetrics = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("Performans izleme servisi başlatıldı");
    }
    
    /**
     * Yeni bir yükleme işlemi kaydet
     * @param hash Dosya hash değeri
     * @param fileSize Dosya boyutu (byte)
     */
    public void recordUpload(String hash, long fileSize) {
        totalUploads.incrementAndGet();
        totalBytesTransferred.addAndGet(fileSize);
        
        TransferMetric metric = transferMetrics.computeIfAbsent(hash, k -> new TransferMetric());
        metric.recordUpload(fileSize);
        
        double fileSizeMB = fileSize / (1024.0 * 1024.0);
        log.info("Yükleme kaydedildi: {} - {} MB dosya yüklendi", hash, String.format("%.2f", fileSizeMB));
    }
    
    /**
     * Yeni bir indirme işlemi kaydet
     * @param hash Dosya hash değeri
     * @param fileSize Dosya boyutu (byte)
     * @param timeMs İndirme süresi (ms)
     */
    public void recordDownload(String hash, long fileSize, long timeMs) {
        totalDownloads.incrementAndGet();
        totalBytesTransferred.addAndGet(fileSize);
        
        TransferMetric metric = transferMetrics.computeIfAbsent(hash, k -> new TransferMetric());
        metric.recordDownload(fileSize, timeMs);
        
        double speedMbps = calculateSpeedMbps(fileSize, timeMs);
        double fileSizeMB = fileSize / (1024.0 * 1024.0);
        double timeSeconds = timeMs / 1000.0;
        
        log.info("İndirme kaydedildi: {} - {} MB dosya, {} saniyede indirildi (hız: {} Mbps)",
                hash, String.format("%.2f", fileSizeMB), String.format("%.2f", timeSeconds), String.format("%.2f", speedMbps));
    }
    
    /**
     * Dosyaya özel transfer metriklerini getir
     * @param hash Dosya hash değeri
     * @return Transfer metriği içeren Optional
     */
    public Optional<TransferMetric> getMetric(String hash) {
        return Optional.ofNullable(transferMetrics.get(hash));
    }
    
    /**
     * Bir hash değerinin metriklerini temizle
     * @param hash Temizlenecek hash değeri
     */
    public void clearMetric(String hash) {
        TransferMetric metric = transferMetrics.remove(hash);
        if (metric != null) {
            log.info("Dosya paylaşımı kapatıldı: {} - Toplam yükleme: {}, Toplam indirme: {}", 
                    hash, metric.getUploadCount(), metric.getDownloadCount());
        }
    }
    
    // Saatte bir istatistikleri logla
    @Scheduled(fixedRate = 3600000)
    public void logStats() {
        double totalTransferMB = totalBytesTransferred.get() / (1024.0 * 1024.0);
        int activeShares = transferMetrics.size();
        
        log.info("Performans İstatistikleri: Toplam Yüklemeler={}, Toplam İndirmeler={}, " +
                 "Toplam Transfer={} MB, Aktif Paylaşımlar={}", 
                totalUploads.get(), 
                totalDownloads.get(),
                String.format("%.2f", totalTransferMB),
                activeShares);
        
        // Aktif paylaşımların detaylı istatistiklerini de logla
        if (activeShares > 0) {
            transferMetrics.forEach((hash, metric) -> {
                log.info("Paylaşım Detayı: {} - Yükleme: {}, İndirme: {}, Ort. Hız: {} Mbps", 
                        hash, 
                        metric.getUploadCount(), 
                        metric.getDownloadCount(),
                        String.format("%.2f", metric.getAverageDownloadSpeedMbps()));
            });
        }
    }
    
    /**
     * Megabit/saniye cinsinden transfer hızını hesapla
     * @param bytes Aktarılan byte miktarı
     * @param timeMs Aktarım süresi (ms)
     * @return Hız (Mbps)
     */
    private double calculateSpeedMbps(long bytes, long timeMs) {
        if (timeMs <= 0) return 0.0;
        // Byte to bits (x8), bytes to megabits (/1024^2), ms to seconds (*1000)
        return bytes * 8.0 / (1024.0 * 1024.0) / (timeMs / 1000.0);
    }
} 
