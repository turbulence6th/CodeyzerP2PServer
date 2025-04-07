package com.codeyzer.p2p.service.monitoring;

import lombok.Getter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dosya transferlerinin metrikleri için veri sınıfı
 */
@Getter
public class TransferMetric {
    
    private final Instant createdAt = Instant.now();
    private Instant lastUsed = Instant.now();
    
    // Upload metrikleri
    private final AtomicLong uploadCount = new AtomicLong(0);
    private final AtomicLong totalUploadBytes = new AtomicLong(0);
    
    // Download metrikleri
    private final AtomicLong downloadCount = new AtomicLong(0);
    private final AtomicLong totalDownloadBytes = new AtomicLong(0);
    private final AtomicLong totalDownloadTimeMs = new AtomicLong(0);
    
    // İstatistik veriler
    private final AtomicLong maxDownloadSpeedBps = new AtomicLong(0);
    private final AtomicLong minDownloadSpeedBps = new AtomicLong(Long.MAX_VALUE);
    
    /**
     * Yükleme işlemi metriklerini günceller
     * @param bytes Yüklenen dosya boyutu
     */
    public void recordUpload(long bytes) {
        uploadCount.incrementAndGet();
        totalUploadBytes.addAndGet(bytes);
        lastUsed = Instant.now();
    }
    
    /**
     * İndirme işlemi metriklerini günceller
     * @param bytes İndirilen dosya boyutu
     * @param timeMs İndirme süresi (ms)
     */
    public void recordDownload(long bytes, long timeMs) {
        downloadCount.incrementAndGet();
        totalDownloadBytes.addAndGet(bytes);
        totalDownloadTimeMs.addAndGet(timeMs);
        
        // En az 1ms olmalı
        long actualTimeMs = Math.max(1, timeMs);
        
        // Byte/saniye cinsinden hız hesapla
        long speedBps = bytes * 1000 / actualTimeMs;
        
        // Minimum ve maksimum hızları güncelle
        updateMaxSpeed(speedBps);
        updateMinSpeed(speedBps);
        
        lastUsed = Instant.now();
    }
    
    /**
     * Maksimum indirme hızını günceller
     * @param speedBps Byte/saniye cinsinden hız
     */
    private void updateMaxSpeed(long speedBps) {
        long currentMax;
        do {
            currentMax = maxDownloadSpeedBps.get();
            if (speedBps <= currentMax) {
                break;
            }
        } while (!maxDownloadSpeedBps.compareAndSet(currentMax, speedBps));
    }
    
    /**
     * Minimum indirme hızını günceller
     * @param speedBps Byte/saniye cinsinden hız
     */
    private void updateMinSpeed(long speedBps) {
        long currentMin;
        do {
            currentMin = minDownloadSpeedBps.get();
            if (speedBps >= currentMin) {
                break;
            }
        } while (!minDownloadSpeedBps.compareAndSet(currentMin, speedBps));
    }
    
    /**
     * Ortalama indirme hızını Byte/saniye cinsinden hesaplar
     * @return Ortalama hız (Bps) veya 0 (hiç indirme yoksa)
     */
    public long getAverageDownloadSpeedBps() {
        if (downloadCount.get() == 0 || totalDownloadTimeMs.get() == 0) {
            return 0;
        }
        return totalDownloadBytes.get() * 1000 / totalDownloadTimeMs.get();
    }
    
    /**
     * Ortalama indirme hızını Megabit/saniye cinsinden hesaplar
     * @return Ortalama hız (Mbps) veya 0 (hiç indirme yoksa)
     */
    public double getAverageDownloadSpeedMbps() {
        long bps = getAverageDownloadSpeedBps();
        return bps * 8.0 / (1024.0 * 1024.0);
    }
} 
