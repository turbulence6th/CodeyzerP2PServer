package com.codeyzer.p2p.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.codeyzer.p2p.dto.FileShareWrapper;
import com.codeyzer.p2p.dto.FileStreamWrapper;
import com.codeyzer.p2p.dto.ShareRequestDTO;
import com.codeyzer.p2p.dto.ShareResponseDTO;
import com.codeyzer.p2p.dto.SocketShareDTO;
import com.codeyzer.p2p.dto.UnshareRequestDTO;
import com.codeyzer.p2p.service.monitoring.PerformanceMonitorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileService {

    private final Map<String, FileShareWrapper> shareMap;
    private final SimpMessagingTemplate template;
    private final HashService hashService;
    private final PerformanceMonitorService monitorService;
    
    @Value("${file-share.buffer-size:8192}")
    private int bufferSize;

    /**
     * Dosya paylaşımı başlatır
     */
    public ShareResponseDTO share(ShareRequestDTO request) {
        if (request.getFilename() == null || request.getSize() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dosya adı ve boyutu zorunludur");
        }

        String shareHash;
        do {
            shareHash = hashService.generateHash();
        } while (shareMap.containsKey(shareHash));

        shareMap.put(shareHash, FileShareWrapper.builder()
                .filename(request.getFilename())
                .size(request.getSize())
                .streamMap(new HashMap<>())
                .build());
                
        return ShareResponseDTO.builder()
                .shareHash(shareHash)
                .build();
    }

    /**
     * Dosya paylaşımını sonlandırır
     */
    public void unshare(UnshareRequestDTO request) {
        if (request.getShareHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Share hash zorunludur");
        }

        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(request.getShareHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı"));
                
        fileShareWrapper.getStreamMap().values().forEach(fileStreamWrapper -> {
            forceClose(fileStreamWrapper.getInputStream());
            forceClose(fileStreamWrapper.getOutputStream());
            fileStreamWrapper.setStatus(-1);
            Optional.ofNullable(fileStreamWrapper.getLatch())
                    .ifPresent(CountDownLatch::countDown);
        });
        
        // Performans metriklerini temizle
        monitorService.clearMetric(request.getShareHash());
        
        shareMap.remove(request.getShareHash());
    }

    /**
     * Dosya yükleme işlemini gerçekleştirir
     */
    public void upload(String shareHash, String streamHash, HttpServletRequest request) 
            throws IOException, BrokenBarrierException, InterruptedException {
        
        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(shareHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı"));
                
        FileStreamWrapper fileStreamWrapper = Optional.ofNullable(fileShareWrapper.getStreamMap().get(streamHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream bulunamadı"));

        try {
            // Doğrudan HTTP isteğinin input stream'ini alıyoruz, 
            // böylece tüm dosya yüklenmeden önce işleme başlayabiliyoruz
            InputStream inputStream = request.getInputStream();
            fileStreamWrapper.setInputStream(inputStream);
            flow(inputStream, fileStreamWrapper.getOutputStream());
            fileStreamWrapper.setStatus(1);
            
            // Yükleme performans metriğini kaydet
            monitorService.recordUpload(shareHash, fileShareWrapper.getSize());
        } finally {
            Optional.ofNullable(fileStreamWrapper)
                    .map(FileStreamWrapper::getLatch)
                    .ifPresent(CountDownLatch::countDown);
        }
    }

    /**
     * Dosya indirme işlemini gerçekleştirir
     */
    public void download(String shareHash, HttpServletRequest request, HttpServletResponse response) 
            throws IOException, InterruptedException {
            
        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(shareHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı"));

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileShareWrapper.getFilename() + "\"");
        response.setHeader("Content-Length", fileShareWrapper.getSize().toString());

        String streamHash;
        do {
            streamHash = hashService.generateHash();
        } while (fileShareWrapper.getStreamMap().containsKey(streamHash));

        long startTime = System.currentTimeMillis();
        
        CountDownLatch latch = new CountDownLatch(1);
        FileStreamWrapper fileStreamWrapper = FileStreamWrapper.builder()
                .latch(latch)
                .outputStream(response.getOutputStream())
                .status(0)
                .build();
        fileShareWrapper.getStreamMap().put(streamHash, fileStreamWrapper);

        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .orElse(request.getRemoteHost());

        template.convertAndSend("/topic/" + shareHash, SocketShareDTO.builder()
                .shareHash(shareHash)
                .streamHash(streamHash)
                .ip(ip)
                .build());

        latch.await();
        
        // İndirme performans metriğini kaydet
        long elapsedTime = System.currentTimeMillis() - startTime;
        monitorService.recordDownload(shareHash, fileShareWrapper.getSize(), elapsedTime);

        fileShareWrapper.getStreamMap().remove(streamHash);
    }

    /**
     * Dosya paylaşımı istatistiklerini getirir
     */
    public Map<String, Object> getStats(String shareHash) {
        // Bu dosya paylaşımı için istatistikleri getir
        Map<String, Object> stats = new HashMap<>();
        
        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(shareHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı"));
                
        stats.put("filename", fileShareWrapper.getFilename());
        stats.put("size", fileShareWrapper.getSize());
        stats.put("activeStreams", fileShareWrapper.getStreamMap().size());
        
        monitorService.getMetric(shareHash).ifPresent(metric -> {
            stats.put("uploadCount", metric.getUploadCount());
            stats.put("downloadCount", metric.getDownloadCount());
            stats.put("averageSpeedMbps", metric.getAverageDownloadSpeedMbps());
            stats.put("maxSpeedMbps", metric.getMaxDownloadSpeedBps().get() * 8.0 / (1024.0 * 1024.0));
            stats.put("minSpeedMbps", metric.getMinDownloadSpeedBps().get() * 8.0 / (1024.0 * 1024.0));
        });
        
        return stats;
    }

    /**
     * Kaynakları temizler
     */
    private void forceClose(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception ignored) {
            // Hata durumunda sessizce devam et
        }
    }

    /**
     * Veri akışını sağlar
     */
    private void flow(InputStream is, OutputStream os) throws IOException {
        // Apache Commons IO ile daha verimli veri akışı
        IOUtils.copy(is, os, bufferSize);
        os.flush();
    }
} 