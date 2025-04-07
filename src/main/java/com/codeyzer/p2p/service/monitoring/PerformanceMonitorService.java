package com.codeyzer.p2p.service.monitoring;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dosya paylaşım işlemlerinin performansını izleyen ve raporlayan servis.
 * Bu servis, dosya indirme ve yükleme işlemlerinin istatistiklerini toplar ve periyodik olarak raporlar.
 */
@Service
@Slf4j
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
        
        log.debug("Yükleme kaydedildi: {} - {} bytes", hash, fileSize);
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
        log.debug("İndirme kaydedildi: {} - {} bytes, {} ms (hız: {} Mbps)",
                 hash, fileSize, timeMs, speedMbps);
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
        transferMetrics.remove(hash);
    }
    
    // Saatte bir istatistikleri logla
    @Scheduled(fixedRate = 3600000)
    public void logStats() {
        log.info("Performans İstatistikleri: Toplam Yüklemeler={}, Toplam İndirmeler={}, Toplam Transfer={} MB", 
                totalUploads.get(), 
                totalDownloads.get(), 
                totalBytesTransferred.get() / (1024.0 * 1024.0));
    }
    
    /**
     * Megabit/saniye cinsinden transfer hızını hesapla
     * @param bytes Aktarılan byte miktarı
     * @param timeMs Aktarım süresi (ms)
     * @return Hız (Mbps)
     */
    private double calculateSpeedMbps(long bytes, long timeMs) {
        // Byte to bits (x8), bytes to megabits (/1024^2), ms to seconds (*1000)
        return bytes * 8.0 / (1024.0 * 1024.0) / (timeMs / 1000.0);
    }
} 
