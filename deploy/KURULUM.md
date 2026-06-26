# RaspelCardTracker - Kurulum Kılavuzu

Kredi kartı harcama takip, bütçe yönetimi, çek takibi, cari hesaplar, personel görev yönetimi ve raporlama sistemi.

## Gereksinimler

| Bileşen | Minimum Sürüm |
|---------|---------------|
| Docker Desktop | 20.10+ |
| İnternet bağlantısı | İlk kurulum + otomatik güncelleme için |
| Boş Disk Alanı | ~3 GB |
| RAM | 4 GB (önerilen) |

## Kurulum (3 Adım)

### 1. Docker Desktop'ı Kurun

https://www.docker.com/products/docker-desktop/ adresinden indirip kurun.

Kurulum sonrası Docker Desktop'ı başlatın. Saat simgesi yeşil olana kadar bekleyin.

### 2. Kurulum Klasörünü Açın

Bu klasörü bilgisayarınızda `C:\CardTracker` gibi bir konuma kopyalayın.

`docker-compose.yml`, `start.bat`, `stop.bat`, `.env.example` dosyaları bu klasörde olmalı.

### 3. Uygulamayı Başlatın

`start.bat` dosyasına **çift tıklayın**.

İlk çalıştırmada:
- `.env` dosyası otomatik oluşturulur (şifreleri değiştirmeniz önerilir)
- Docker imajı indirilir (~700 MB, bir kereye mahsus)
- PostgreSQL veritabanı oluşturulur
- Uygulama başlatılır (~1 dakika)

## Uygulamaya Erişim

Tarayıcıda `http://localhost:8080` adresini açın.

**Varsayılan hesap:**
- Kullanıcı adı: `admin`
- Şifre: `admin123`

> İlk girişten sonra şifrenizi değiştirmeniz önerilir.

## Firma Adını Değiştirme

Admin hesabıyla giriş yapın → **Profilim** sayfası → "Firma Adı Değiştir" bölümünden firmanızın adını yazıp kaydedin. Uygulamanın her yerinde bu isim görünecektir.

## Telegram Bot Bağlantısı (Opsiyonel)

Uygulama, Telegram üzerinden bildirim almak için bot desteği sunar:

1. **Profilim** sayfasına gidin
2. "Telegram'a Bağlan" butonuna tıklayın
3. Size verilen 6 haneli kodu kopyalayın
4. Telegram'da [@raspel_fin_bot](https://t.me/raspel_fin_bot) bot'unu başlatın
5. `/start` yazıp doğrulama kodunu gönderin
6. Bağlantı kurulduğunda profil sayfanızda "Bağlı" gözükecektir

Bot'un çalışması için `.env` dosyasında `TELEGRAM_BOT_TOKEN` tanımlı olmalıdır.

## Ortam Değişkenleri (.env)

`.env` dosyasındaki ayarlar:

| Değişken | Açıklama | Zorunlu |
|----------|----------|---------|
| `DB_USER` | Veritabanı kullanıcı adı | Evet |
| `DB_PASS` | Veritabanı şifresi | Evet |
| `DB_URL` | Veritabanı bağlantı adresi | Evet |
| `DOCKER_IMAGE` | Docker imaj adresi | Evet |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token'ı | Hayır |

Bot token'ı yoksa bot devre dışı kalır, diğer tüm özellikler çalışmaya devam eder.

## Otomatik Güncelleme

Uygulama **Watchtower** ile otomatik güncellenir. Yeni bir sürüm yayınlandığında, sistem 5 dakika içinde otomatik olarak güncellenir. Verileriniz korunur, hiçbir şey silinmez.

Hiçbir şey yapmanıza gerek yok - sabah işe geldiğinizde uygulama güncellenmiş olur.

## Durdurma

`stop.bat` dosyasına çift tıklayın.

## Veritabanı Sıfırlama (Tüm Veriler Silinir!)

```cmd
docker compose down -v
docker compose up -d
```

## Servisler ve Portlar

| Servis | Port | Açıklama |
|--------|------|----------|
| cardtracker-app | 8080 | Ana uygulama |
| cardtracker-db | 5432 | Veritabanı (sadece yerel) |
| cardtracker-db-backup | - | Otomatik yedekleme |
| cardtracker-watchtower | - | Otomatik güncelleme |

## Yedekleme

Veritabanı yedekleri `backups/` klasöründe otomatik tutulur:
- `daily/` - Günlük (14 gün saklanır)
- `weekly/` - Haftalık (8 hafta)
- `monthly/` - Aylık (12 ay)
- `last/` - En son yedek

## Sorun Giderme

**"Docker çalışmıyor" hatası:**
- Docker Desktop'ın tamamen başladığından emin olun (saat simgesi yeşil)

**"İmaj indirilemedi" hatası:**
- İnternet bağlantınızı kontrol edin

**Uygulama açılmıyorsa:**
- 8080 portunun başka bir uygulama tarafından kullanılmadığını kontrol edin
- `docker compose logs app` ile logları kontrol edin
- Docker Desktop'ı yeniden başlatıp tekrar deneyin

**Tarayıcıda boş sayfa:**
- Uygulamanın tamamen başlaması 30-60 saniye sürebilir, bekleyip sayfayı yenileyin

## Klasör Yapısı

```
C:\CardTracker\
├── docker-compose.yml    (servis tanımları)
├── .env                  (veritabanı şifreleri - otomatik oluşur)
├── .env.example          (şablon)
├── start.bat             (başlatma)
├── stop.bat              (durdurma)
├── KURULUM.md            (bu dosya)
├── uploads/              (belge ekleri - otomatik oluşur)
└── backups/              (veritabanı yedekleri - otomatik oluşur)
```

## Teknik Bilgiler

- Backend: Spring Boot 3.4, Java 21
- Veritabanı: PostgreSQL 15
- Arayüz: Vaadin 24
- Güncelleme: Watchtower (her 5 dakikada kontrol)

---

**Yazılım:** Rasim Tuzluoğlu | © 2026 RasPel Co.
