-- Kredi kartlarına departman, kart sahibi ve ödeme vadesi bilgilerini ekle
ALTER TABLE card ADD COLUMN department VARCHAR(100);
ALTER TABLE card ADD COLUMN holder_name VARCHAR(100);
ALTER TABLE card ADD COLUMN due_day INT NOT NULL DEFAULT 10;

-- Harcamalara çoklu para birimi ve evrak yükleme desteği ekle
ALTER TABLE expense ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'TRY';
ALTER TABLE expense ADD COLUMN original_amount NUMERIC(15,2);
ALTER TABLE expense ADD COLUMN exchange_rate NUMERIC(15,4) NOT NULL DEFAULT 1.0000;
ALTER TABLE expense ADD COLUMN receipt_path VARCHAR(255);
ALTER TABLE expense ADD COLUMN receipt_content_type VARCHAR(100);

-- Mevcut harcamalar için original_amount değerini güncelle
UPDATE expense SET original_amount = total_amount WHERE original_amount IS NULL;

-- Varsayılan admin kullanıcısının hatalı olan şifre hash değerini admin123 ile güncelle
UPDATE app_user SET password = '$2a$10$jB7z875KYNowLT8.sq9tcu3GAaJL///lEp7LcAYFCaF.PnY9yTUr6' WHERE username = 'admin' AND password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
