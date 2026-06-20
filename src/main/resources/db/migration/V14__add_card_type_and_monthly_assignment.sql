-- Kredi/Banka Karti tipi ve aylik atama alani ekleme
ALTER TABLE card 
ADD COLUMN card_type VARCHAR(30) DEFAULT 'CREDIT_CARD',
ADD COLUMN monthly_assignment NUMERIC(19, 2);

-- Mevcut kayitlar icin varsayilan atama
UPDATE card SET card_type = 'CREDIT_CARD' WHERE card_type IS NULL;
