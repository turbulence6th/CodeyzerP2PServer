package com.codeyzer.p2p.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeyzer.p2p.dto.ShareRequestDTO;
import com.codeyzer.p2p.dto.ShareResponseDTO;
import com.codeyzer.p2p.dto.UnshareRequestDTO;
import com.codeyzer.p2p.service.FileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/share")
    public ShareResponseDTO share(@RequestBody ShareRequestDTO request) {
        return fileService.share(request);
    }

    @PostMapping("/unshare")
    public void unshare(@RequestBody UnshareRequestDTO request) {
        fileService.unshare(request);
    }

    @PostMapping("/upload/{shareHash}/{streamHash}")
    public void upload(
            @PathVariable String shareHash, 
            @PathVariable String streamHash, 
            HttpServletRequest request) throws IOException, BrokenBarrierException, InterruptedException {
        fileService.upload(shareHash, streamHash, request);
    }

    @GetMapping("/download/{shareHash}")
    public void download(
            @PathVariable String shareHash, 
            HttpServletRequest request, 
            HttpServletResponse response) throws IOException, InterruptedException {
        fileService.download(shareHash, request, response);
    }
    
    @GetMapping("/stats/{shareHash}")
    public Map<String, Object> getStats(@PathVariable String shareHash) {
        return fileService.getStats(shareHash);
    }
}
