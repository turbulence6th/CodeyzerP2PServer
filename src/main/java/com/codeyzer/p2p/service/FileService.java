package com.codeyzer.p2p.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.codeyzer.p2p.dto.FileShareWrapper;
import com.codeyzer.p2p.dto.FileStreamWrapper;
import com.codeyzer.p2p.dto.ShareRequestDTO;
import com.codeyzer.p2p.dto.ShareResponseDTO;
import com.codeyzer.p2p.dto.SocketShareDTO;
import com.codeyzer.p2p.dto.UnshareRequestDTO;
import com.codeyzer.p2p.dto.FileInfoDTO;
import com.codeyzer.p2p.service.monitoring.PerformanceMonitorService;
import com.codeyzer.p2p.config.FileShareProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final Map<String, FileShareWrapper> shareMap;
    private final SimpMessagingTemplate template;
    private final HashService hashService;
    private final PerformanceMonitorService monitorService;
    private final FileShareProperties fileShareProperties;

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

        long currentTime = System.currentTimeMillis();
        String ownerToken = UUID.randomUUID().toString();

        FileShareWrapper newShare = FileShareWrapper.builder()
                .filename(request.getFilename())
                .size(request.getSize())
                .streamMap(new ConcurrentHashMap<>())
                .creationTimestamp(currentTime)
                .lastHeartbeatTimestamp(currentTime)
                .ownerToken(ownerToken)
                .build();

        shareMap.put(shareHash, newShare);
                
        return ShareResponseDTO.builder()
                .shareHash(shareHash)
                .ownerToken(ownerToken)
                .build();
    }

    /**
     * Dosya paylaşımını sonlandırır
     */
    public void unshare(UnshareRequestDTO request) {
        if (request.getShareHash() == null || request.getOwnerToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Share hash ve owner token zorunludur");
        }

        FileShareWrapper fileShareWrapper = shareMap.get(request.getShareHash());

        if (fileShareWrapper == null) {
            log.info("Unshare request for non-existent or already cleaned up share: {}", request.getShareHash());
            return;
        }

        if (!fileShareWrapper.getOwnerToken().equals(request.getOwnerToken())) {
            log.warn("Unauthorized unshare attempt for share: {} with token: {}", request.getShareHash(), request.getOwnerToken());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz işlem: Geçersiz sahip tokenı");
        }
        
        log.info("Unsharing file: {} requested by owner.", request.getShareHash());
        try {
             fileShareWrapper.getStreamMap().values().forEach(fileStreamWrapper -> {
                forceClose(fileStreamWrapper.getInputStream());
                forceClose(fileStreamWrapper.getOutputStream());
                fileStreamWrapper.setStatus(-1);
                Optional.ofNullable(fileStreamWrapper.getLatch())
                        .ifPresent(CountDownLatch::countDown);
            });
        } finally {
            monitorService.clearMetric(request.getShareHash());
            shareMap.remove(request.getShareHash());
            log.info("Successfully unshared: {}", request.getShareHash());
        }        
    }

    /**
     * Dosya yükleme işlemini gerçekleştirir
     */
    public void upload(String shareHash, String streamHash, HttpServletRequest request)
            throws IOException {
        
        // Owner Token kontrolü
        String ownerTokenHeader = request.getHeader("X-Owner-Token");
        if (ownerTokenHeader == null || ownerTokenHeader.trim().isEmpty()) {
            log.warn("Upload attempt for share {} without X-Owner-Token header.", shareHash);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Eksik sahip tokenı");
        }

        FileShareWrapper fileShareWrapper = shareMap.get(shareHash);
        if (fileShareWrapper == null) {
             log.warn("Upload attempt for non-existent share: {}", shareHash);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya paylaşımı bulunamadı");
        }

        // Owner token'ı doğrula
        if (!fileShareWrapper.getOwnerToken().equals(ownerTokenHeader)) {
             log.warn("Unauthorized upload attempt for share: {} with token: {}", shareHash, ownerTokenHeader);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz işlem: Geçersiz sahip tokenı");
        }

        // Token doğrulandı, işleme devam et
        log.info("Authorized upload starting for share: {} with stream: {}", shareHash, streamHash);
        FileStreamWrapper fileStreamWrapper = fileShareWrapper.getStreamMap().get(streamHash);
        if (fileStreamWrapper == null) {
             log.warn("Upload attempt for non-existent stream: {} for share: {}", streamHash, shareHash);
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream bulunamadı");
        }

        try {
            InputStream inputStream = request.getInputStream();
            fileStreamWrapper.setInputStream(inputStream);
            skipMultipartHeadersAndBoundary(inputStream);
            flow(inputStream, fileStreamWrapper.getOutputStream());
            fileStreamWrapper.setStatus(1);
            monitorService.recordUpload(shareHash, fileShareWrapper.getSize());
        } finally {
            Optional.ofNullable(fileStreamWrapper)
                    .map(FileStreamWrapper::getLatch)
                    .ifPresent(CountDownLatch::countDown);
        }
    }
    
    /**
     * Multipart sınırlarını ve başlıkları atlayarak dosya içeriğine geçer
     */
    private void skipMultipartHeadersAndBoundary(InputStream inputStream) throws IOException {
        // Basit bir multipart ayrıştırıcı
        byte[] buffer = new byte[8192];
        int read;
        StringBuilder headerBuilder = new StringBuilder();
        boolean inHeaders = true;
        
        while (inHeaders && (read = inputStream.read(buffer, 0, 1)) != -1) {
            char c = (char) buffer[0];
            headerBuilder.append(c);
            
            // İki kez CRLF görürsek, başlıklar bitmiş ve dosya içeriğine ulaşmış demektir
            if (headerBuilder.toString().endsWith("\r\n\r\n")) {
                inHeaders = false;
            }
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
        byte[] buffer = new byte[fileShareProperties.getBufferSize()];
        int bytesRead;
        long totalBytes = 0;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
            
            if (totalBytes % (10 * 1024 * 1024) < fileShareProperties.getBufferSize()) {
                os.flush();
            }
        }
        os.flush();
    }

    /**
     * Verilen hash'e ait dosya bilgilerini döndürür
     */
    public FileInfoDTO getFileInfo(String hash) {
        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(hash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı hash: " + hash));

        // Dosya tipi için basit bir çıkarım yapılabilir veya FileShareWrapper'a eklenebilir.
        // Şimdilik dosya adından uzantıyı alarak basit bir çıkarım yapalım.
        String fileName = fileShareWrapper.getFilename();
        String fileType = "unknown";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            fileType = fileName.substring(lastDotIndex + 1);
        }

        return new FileInfoDTO(
                fileShareWrapper.getFilename(),
                fileShareWrapper.getSize(),
                fileType
        );
    }

    /**
     * Verilen shareHash için son kalp atışı zamanını günceller.
     * @param shareHash Kalp atışı alınan paylaşımın hash'i.
     */
    public void updateHeartbeat(String shareHash, String ownerToken) {
        FileShareWrapper shareWrapper = shareMap.get(shareHash);
        if (shareWrapper != null) {
            if (shareWrapper.getOwnerToken() != null && shareWrapper.getOwnerToken().equals(ownerToken)) {
                shareWrapper.setLastHeartbeatTimestamp(System.currentTimeMillis());
                log.debug("Heartbeat updated for share: {} with matching owner token", shareHash);
            } else {
                log.warn("Heartbeat received for share: {} with MISMATCHING owner token. Provided: {}, Expected: {}. Heartbeat NOT updated.", 
                         shareHash, ownerToken, shareWrapper.getOwnerToken());
            }
        } else {
            log.warn("Heartbeat received for non-existent or already cleaned up share: {}", shareHash);
        }
    }
} 