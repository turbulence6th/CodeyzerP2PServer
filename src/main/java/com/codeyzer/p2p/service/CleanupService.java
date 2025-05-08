package com.codeyzer.p2p.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codeyzer.p2p.config.FileShareProperties;
import com.codeyzer.p2p.dto.FileShareWrapper;
import com.codeyzer.p2p.dto.UnshareRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "file-share.heartbeat", name = "enabled", havingValue = "true")
public class CleanupService {

    private final Map<String, FileShareWrapper> shareMap;
    private final FileService fileService;
    private final FileShareProperties fileShareProperties;
    
    @Scheduled(fixedRateString = "${file-share.heartbeat.check-interval-seconds:60}000")
    public void cleanupStaleShares() {
        log.info("Starting stale shares cleanup process...");
        long currentTimeMillis = System.currentTimeMillis();
        long staleThresholdMillis = TimeUnit.SECONDS.toMillis(fileShareProperties.getHeartbeat().getStaleTimeoutSeconds());
        long initialGracePeriodMillis = TimeUnit.SECONDS.toMillis(fileShareProperties.getHeartbeat().getInitialGracePeriodSeconds());

        List<String> removedShareHashes = new ArrayList<>();

        shareMap.forEach((hash, shareWrapper) -> {
            boolean isStale = false;
            long lastHeartbeat = shareWrapper.getLastHeartbeatTimestamp(); 
            long creationTime = shareWrapper.getCreationTimestamp();     

            if (lastHeartbeat == 0L) {
                if ((currentTimeMillis - creationTime) > initialGracePeriodMillis) {
                    log.warn("Share '{}' never received a heartbeat and initial grace period of {}s expired. Marked as stale.", hash, fileShareProperties.getHeartbeat().getInitialGracePeriodSeconds());
                    isStale = true;
                }
            } else {
                if ((currentTimeMillis - lastHeartbeat) > staleThresholdMillis) {
                    log.warn("Share '{}' is stale. Last heartbeat was at {} ({} seconds ago). Stale threshold is {}s.",
                            hash, Instant.ofEpochMilli(lastHeartbeat), TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis - lastHeartbeat), fileShareProperties.getHeartbeat().getStaleTimeoutSeconds());
                    isStale = true;
                }
            }

            if (isStale) {
                if (shareWrapper.getStreamMap() == null || shareWrapper.getStreamMap().isEmpty()) {
                    log.info("Attempting to unshare stale share: {}", hash);
                    try {
                        fileService.unshare(UnshareRequestDTO.builder().shareHash(hash).ownerToken(shareWrapper.getOwnerToken()).build());
                        removedShareHashes.add(hash);
                        log.info("Successfully unshared stale share: {}", hash);
                    } catch (Exception e) {
                        log.error("Error while unsharing stale share '{}': {}", hash, e.getMessage(), e);
                    }
                } else {
                    log.info("Share '{}' is stale but has {} active streams. Skipping cleanup for now.", hash, shareWrapper.getStreamMap().size());
                }
            }
        });

        if (!removedShareHashes.isEmpty()) {
            log.info("Stale shares cleanup process finished. Removed {} shares: {}", removedShareHashes.size(), removedShareHashes);
        } else {
            log.info("Stale shares cleanup process finished. No stale shares were found or removed.");
        }
    }
} 