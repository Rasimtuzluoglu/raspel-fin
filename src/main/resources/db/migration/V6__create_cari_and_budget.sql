-- Cari (Contact) Tablosu
CREATE TABLE contact (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    tax_no VARCHAR(50),
    tax_office VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100),
    type VARCHAR(20) NOT NULL, -- 'CUSTOMER', 'SUPPLIER', 'BOTH'
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Çek tablosunu Cari ile ilişkilendir
ALTER TABLE cheque ADD COLUMN contact_id BIGINT REFERENCES contact(id) ON DELETE SET NULL;

-- Mevcut çeklerdeki karşı taraf (party) verilerini Cari tablosuna aktar
INSERT INTO contact (name, type)
SELECT DISTINCT party, 'BOTH' 
FROM cheque 
WHERE party IS NOT NULL AND TRIM(party) <> ''
ON CONFLICT (name) DO NOTHING;

-- Mevcut çeklerin contact_id alanlarını güncelle
UPDATE cheque c
SET contact_id = con.id
FROM contact con
WHERE c.party = con.name;

-- Departman Bütçe Tablosu
CREATE TABLE department_budget (
    id BIGSERIAL PRIMARY KEY,
    department VARCHAR(100) NOT NULL,
    budget_year INT NOT NULL,
    budget_month INT NOT NULL,
    budget_limit NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dept_year_month UNIQUE(department, budget_year, budget_month)
);
