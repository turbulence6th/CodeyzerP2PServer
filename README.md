# Codeyzer P2P Sunucusu

Bu proje, P2P (Peer-to-Peer) dosya paylaşımı için bir sunucu uygulamasıdır.

## Özellikler

- WebSocket tabanlı P2P bağlantı yönetimi
- Dosya paylaşımı
- Kullanıcılar arası iletişim

## Gereksinimler

- Java 17
- Maven 3.6+

## Kurulum

Projeyi klonlayın ve Maven ile derleyin:

```bash
git clone https://github.com/yourusername/CodeyzerP2PServer.git
cd CodeyzerP2PServer
mvn clean install
```

## Çalıştırma

Maven ile uygulamayı başlatın:

```bash
mvn spring-boot:run
```

Veya JAR dosyasından:

```bash
java -jar target/codeyzer-p2p-0.0.1-SNAPSHOT.jar
```

## Teknolojiler

- Spring Boot 2.5.5
- WebSocket
- Maven
- Lombok
- MapStruct

## Lisans

Bu proje [LICENSE](LICENSE) dosyasında belirtilen lisans altında dağıtılmaktadır. 