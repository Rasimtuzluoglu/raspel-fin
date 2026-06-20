# RaspelCardTracker - Kurulum Kılavuzu

Kredi kartı harcama takip ve finans yönetim sistemi.

## Gereksinimler

| Bileşen | Minimum Sürüm |
|---------|---------------|
| Docker Desktop | 20.10+ |
| İnternet bağlantısı | Sürekli (güncelleme için) |
| Boş Disk Alanı | ~3 GB |
| RAM | 4 GB (önerilen) |

## Kurulum (3 Adım)

### 1. Docker Desktop'ı Kurun
https://www.docker.com/products/docker-desktop/

### 2. Klasörü Bilgisayara Kopyalayın
Bu klasörü `C:\CardTracker` gibi bir konuma kopyalayın.

### 3. start.bat'e Çift Tıklayın
Her şey otomatik. İlk çalıştırmada ~5 dakika sürebilir.

## Giriş
- **Adres:** http://localhost:8080
- **Kullanıcı:** admin
- **Şifre:** admin

## Firma Adı Değiştirme
Admin girişi → Profilim → "Firma Adı Değiştir"

## Otomatik Güncelleme
Watchtower her 5 dakikada bir yeni sürüm kontrol eder. Güncelleme varsa otomatik uygular. Veriler kaybolmaz.

## Durdurma
`stop.bat`'e çift tıklayın.

## Portlar
| Servis | Port |
|--------|------|
| Uygulama | 8080 |
| Veritabanı | 5432 (yerel) |

## Sorun Giderme
- Docker çalışmıyorsa: Docker Desktop'ı başlatın
- Uygulama gelmiyorsa: 1 dakika bekleyip sayfayı yenileyin
- Logları gör: `docker compose logs app`

---

© 2026 RasPel | Rasim Tuzluoğlu
