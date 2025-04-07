# Codeyzer P2P Server Dokümantasyonu

Bu dokümantasyon, Codeyzer P2P Server uygulamasının API'leri hakkında temel bilgiler içermektedir.

## Dokümantasyon İçeriği

- [API Endpoint'leri](endpoints.md): HTTP ve WebSocket endpoint'lerinin detaylı açıklaması

## Sistem Gereksinimleri

- Java 17 veya üzeri
- Maven 3.8 veya üzeri

## Hızlı Başlangıç

### Geliştirme Ortamında Çalıştırma

```bash
# Proje dizininde
mvn spring-boot:run
```

### Üretim Ortamında Çalıştırma

```bash
# Proje dizininde
mvn spring-boot:run -Dspring.profiles.active=prod
```

## Mimari Genel Bakış

Codeyzer P2P Server, dosya paylaşımı için bir aracı olarak çalışır. Dosya paylaşımı başlatma, indirme ve paylaşım sonlandırma işlemleri için HTTP endpoint'leri, gerçek zamanlı bildirimler için ise WebSocket kullanır.

## Destek ve İletişim

Geliştirici ile iletişime geçmek için:

- E-posta: codeyzer@example.com
- GitHub: https://github.com/codeyzer/p2p-server 