# RaspelCardTracker

Kredi kartı harcama takibi, bütçe yönetimi, çek takibi, cari hesaplar, personel görev yönetimi ve raporlama için geliştirilmiş web tabanlı finans yönetim uygulaması.

## Teknoloji Altyapısı

| Katman | Teknoloji |
|---|---|
| Dil | Java 21 |
| Framework | Spring Boot 3.4 |
| UI | Vaadin 24 (server-side Java) |
| Veritabanı | PostgreSQL 15 |
| Migration | Flyway |
| Güvenlik | Spring Security (BCrypt) |
| Build | Maven |
| Container | Docker |
| Raporlama | Apache POI (Excel), OpenPDF (PDF), ApexCharts |

## Özellikler

- **Kart Yönetimi**: Kredi/banka kartı tanımları, limit, renk, hesap kesim/son ödeme günleri
- **Harcama Takibi**: Taksitli harcama girişi, Excel/PDF dışa aktarım
- **Bütçe Yönetimi**: Departman bazlı aylık bütçe limitleri ve görsel ilerleme takibi
- **Çek Takibi**: Çek giriş/çıkış, durum yaşam döngüsü
- **Cari Hesaplar**: İletişim/firma yönetimi
- **Personel & Görev**: Personel tanımları ve görev atama/takip
- **Notlar**: Hatırlatıcılı not sistemi
- **Raporlama**: Aylık otomatik PDF raporu, Excel dışa aktarım
- **Ortalama Vade**: CSV ile ağırlıklı ortalama vade hesaplama
- **Denetim Kaydı**: Tüm veri değişikliklerinin kaydı
- **Yedekleme**: Manuel pg_dump yedekleme ve geri yükleme
- **Karanlık Mod**: Kullanıcı bazlı tema desteği

## Hızlı Başlangıç

### Gereksinimler

- Docker ve Docker Compose
- Java 21 (geliştirme için)

### Docker ile Çalıştırma (Production)

```bash
# .env dosyasını oluşturun
cp .env.example .env
# .env içindeki DB_USER ve DB_PASS değerlerini düzenleyin

# Uygulamayı başlatın
docker compose -f docker-compose.deploy.yml up -d
```

Uygulama `http://localhost:8080` adresinde çalışır.

### Geliştirme Ortamı

```bash
# .env dosyasını oluşturun
cp .env.example .env

# Geliştirme ortamını başlatın (yerel build)
docker compose up -d

# Veya Maven ile doğrudan çalıştırın
./mvnw spring-boot:run
```

Profil ile çalıştırma:
```bash
./mvnw spring-boot:run -Pproduction
```

### Testleri Çalıştırma

```bash
./mvnw test
```

Test kapsama raporu:
```bash
./mvnw test jacoco:report
# target/site/jacoco/index.html
```

## Ortam Değişkenleri

| Değişken | Açıklama |
|---|---|
| `DB_URL` | PostgreSQL bağlantı URL'i |
| `DB_USER` | Veritabanı kullanıcı adı |
| `DB_PASS` | Veritabanı şifresi |
| `DOCKER_IMAGE` | (opsiyonel) Docker imaj adresi |
| `REPORTS_DIR` | (opsiyonel) Rapor dizini, varsayılan `/app/reports` |

## Veritabanı Migration

Flyway otomatik migration yönetir. Migration dosyaları `src/main/resources/db/migration/` altında bulunur.

## Proje Yapısı

```
src/main/java/com/raspel/cardtracker/
├── config/          # Security, Scheduling, Exception handling
├── domain/          # İş mantığı ve servis katmanı
│   ├── audit/       # Denetim kaydı
│   ├── backup/      # Yedekleme/geri yükleme
│   ├── budget/      # Bütçe yönetimi
│   ├── card/        # Kart yönetimi
│   ├── cheque/      # Çek takibi
│   ├── contact/     # Cari hesaplar
│   ├── department/  # Departmanlar
│   ├── employee/    # Personel ve görevler
│   ├── expense/     # Harcama takibi
│   ├── note/        # Notlar
│   ├── reminder/    # Ödeme hatırlatıcıları
│   ├── report/      # Rapor üretimi
│   ├── settings/    # Uygulama ayarları
│   ├── user/        # Kullanıcı yönetimi
│   └── vade/        # Vade hesaplama
└── ui/              # Vaadin kullanıcı arayüzü
```

## Lisans

RasPel Co. - Tüm hakları saklıdır.
