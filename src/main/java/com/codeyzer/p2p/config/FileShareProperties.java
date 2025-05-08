package com.codeyzer.p2p.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated; // Gerekirse validasyon için

// import javax.validation.constraints.Min; // Gerekirse validasyon için

@Component
@ConfigurationProperties(prefix = "file-share")
@Getter
@Setter
@Validated // Gerekirse @Min gibi validasyonları etkinleştirmek için
public class FileShareProperties {

    /**
     * Dosya transferi sırasında kullanılacak buffer boyutu (byte).
     */
    // @Min(1024) // Örnek validasyon
    private int bufferSize = 8192;

    /**
     * Oluşturulacak paylaşım hash'lerinin uzunluğu.
     */
    // @Min(4)
    private int hashLength = 4;

    /**
     * Kalp atışı tabanlı temizleme mekanizması ayarları.
     */
    private HeartbeatCleanupProperties heartbeat = new HeartbeatCleanupProperties();

    // Kaldırıldı: Eski inaktivite tabanlı temizleme ayarları
    // private InactivityCleanupProperties cleanup = new InactivityCleanupProperties();


    // --- İç Sınıflar ---

    @Getter
    @Setter
    public static class HeartbeatCleanupProperties {
        /**
         * Paylaşımın bayatlamış (stale) sayılması için son kalp atışından sonra geçmesi gereken süre (saniye).
         */
        // @Min(30)
        private long staleTimeoutSeconds = 120;

        /**
         * Paylaşım oluşturulduktan sonra ilk kalp atışı için tanınacak ek süre (saniye).
         */
        // @Min(60)
        private long initialGracePeriodSeconds = 180;
    }

}