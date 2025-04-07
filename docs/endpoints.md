# Uygulama Endpoint'leri

Codeyzer P2P Server, dosya paylaşımı için HTTP ve WebSocket endpoint'leri sağlar. Bu belge, mevcut endpoint'leri ve kullanım şekillerini açıklar.

> **ÖNEMLİ NOT:** Uygulama, `spring.servlet.multipart.enabled=false` ayarı ile çalışır. Bu ayar, dosya yükleme sırasında tüm dosyanın yüklenmesini beklemeden anında işlemeyi sağlar. Bu ayarı değiştirmeyiniz, aksi takdirde dosya paylaşım sistemi düzgün çalışmayacaktır.

## HTTP Endpoint'leri

### Dosya Yükleme ve İndirme

| Endpoint | Metod | Açıklama |
|----------|--------|------------|
| `/file/upload` | POST | Dosya yükleme işlemi için kullanılır |
| `/file/download/{hash}` | GET | Belirtilen hash'e sahip dosyayı indirir |

### Örnek Kullanım

#### Dosya Yükleme:

```bash
curl -X POST http://localhost:8080/file/upload \
  -F "file=@dosya.txt"
```

#### Dosya İndirme:

```bash
curl -X GET http://localhost:8080/file/download/{hash} --output indirilen-dosya.txt
```

## WebSocket Endpoint'leri

WebSocket bağlantıları için ana endpoint:

```
/gs-guide-websocket
```

### Mesaj Destinasyonları

| Destination | Açıklama |
|-------------|----------|
| `/app/...` | Mesaj gönderimi için başlangıç noktası |

### SockJS Desteği

Uygulama, WebSocket desteği olmayan tarayıcılar için SockJS kullanır. Bu, şu endpoint üzerinden erişilebilir:

```
/gs-guide-websocket/**
```

## CORS Yapılandırması

### Geliştirme Ortamı

İzin verilen kaynaklar:
- `http://localhost:3000`
- `http://localhost:8080`

### Üretim Ortamı

İzin verilen kaynaklar:
- `https://codeyzerp2p.tail9fb8f4.ts.net`

## Erişim Kuralları

- HTTP istekleri için `OPTIONS` ön kontrol istekleri desteklenmektedir.
- İzin verilen HTTP metodları: GET, POST, PUT, DELETE, OPTIONS
- İzin verilen HTTP başlıkları: Origin, Content-Type, Accept, Authorization
- CORS tarayıcı önbelleği süresi: 86400 saniye (24 saat)

## İstek ve Yanıt Biçimleri

### Dosya Yükleme Yanıtı

```json
{
  "hash": "58e0494c51d30eb3494f7c9198986bb90885620f",
  "fileName": "ornek-dosya.txt",
  "size": 1024
}
```

### Hata Yanıtları

```json
{
  "error": "Dosya bulunamadı",
  "status": 404,
  "timestamp": "2023-01-01T12:00:00Z"
}
```

## WebSocket Mesaj Biçimleri

WebSocket üzerinden iletilen mesajlar JSON formatında olmalıdır.

### Örnek Mesaj:

```json
{
  "type": "FILE_SHARE",
  "content": {
    "hash": "58e0494c51d30eb3494f7c9198986bb90885620f",
    "fileName": "ornek-dosya.txt"
  },
  "sender": "user1",
  "timestamp": "2023-01-01T12:00:00Z"
}
``` 