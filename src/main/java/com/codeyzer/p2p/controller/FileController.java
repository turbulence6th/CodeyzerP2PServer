package com.codeyzer.p2p.controller;

import com.codeyzer.p2p.dto.*;
import com.codeyzer.p2p.service.HashService;
import com.codeyzer.p2p.service.monitoring.PerformanceMonitorService;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;

@RestController
@RequestMapping("/file")
public class FileController {

    private final Map<String, FileShareWrapper> shareMap;
    private final SimpMessagingTemplate template;
    private final HashService hashService;
    private final PerformanceMonitorService monitorService;
    
    @Value("${file-share.buffer-size:8192}")
    private int bufferSize;

    public FileController(
            Map<String, FileShareWrapper> shareMap, 
            SimpMessagingTemplate template, 
            HashService hashService,
            PerformanceMonitorService monitorService) {
        this.shareMap = shareMap;
        this.template = template;
        this.hashService = hashService;
        this.monitorService = monitorService;
    }

    @PostMapping("/share")
    public ShareResponseDTO share(@RequestBody ShareRequestDTO request) {
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

    @PostMapping("/unshare")
    public void unshare(@RequestBody UnshareRequestDTO request) {
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

    private void forceClose(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception ignored) {
            // Hata durumunda sessizce devam et
        }
    }

    @PostMapping("/upload/{shareHash}/{streamHash}")
    public void upload(
            @PathVariable String shareHash, 
            @PathVariable String streamHash, 
            HttpServletRequest request) throws IOException, FileUploadException, BrokenBarrierException, InterruptedException {
        
        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(shareHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı"));
                
        FileStreamWrapper fileStreamWrapper = Optional.ofNullable(fileShareWrapper.getStreamMap().get(streamHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream bulunamadı"));
        
        long startTime = System.currentTimeMillis();
        
        try {
            FileItemIterator iterator = getItemIterator(request);
            if (iterator.hasNext()) {
                FileItemStream item = iterator.next();

                if (!item.isFormField()) {
                    InputStream inputStream = item.openStream();
                    fileStreamWrapper.setInputStream(inputStream);
                    flow(inputStream, fileStreamWrapper.getOutputStream());
                    fileStreamWrapper.setStatus(1);
                    
                    // Yükleme performans metriğini kaydet
                    monitorService.recordUpload(shareHash, fileShareWrapper.getSize());
                }
            }
        } finally {
            Optional.ofNullable(fileStreamWrapper)
                    .map(FileStreamWrapper::getLatch)
                    .ifPresent(CountDownLatch::countDown);
        }
    }

    FileItemIterator getItemIterator(HttpServletRequest request) throws FileUploadException, IOException {
        return new ServletFileUpload().getItemIterator(request);
    }

    @GetMapping("/download/{shareHash}")
    public void download(
            @PathVariable String shareHash, 
            HttpServletRequest request, 
            HttpServletResponse response) throws IOException, InterruptedException {
            
        FileShareWrapper fileShareWrapper = Optional.ofNullable(shareMap.get(shareHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı"));

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileShareWrapper.getFilename() + "\"");
        response.setHeader("Content-Length", fileShareWrapper.getSize().toString());

        String streamHash;
        do {
            streamHash = hashService.generateHash();
        } while (fileShareWrapper.getStreamMap().containsKey(streamHash));

        long startTime = System.currentTimeMillis();
        
        CountDownLatch latch = getLatch();
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

    CountDownLatch getLatch() {
        return new CountDownLatch(1);
    }

    private void flow(InputStream is, OutputStream os) throws IOException {
        // Yapılandırılmış buffer boyutu kullanarak performans artışı
        byte[] buffer = new byte[bufferSize];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
            os.flush(); // Veriyi hemen gönder
        }
    }
    
    @GetMapping("/stats/{shareHash}")
    public Map<String, Object> getStats(@PathVariable String shareHash) {
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
}
