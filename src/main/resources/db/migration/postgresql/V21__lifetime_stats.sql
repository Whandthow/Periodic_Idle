-- Лічильник престижів (реінкарнацій) і момент створення save — база для сторінки
-- "Статистика" (сумарний час гри, кількість реінкарнацій). Для вже існуючих saves
-- created_at бекфіляється на CURRENT_TIMESTAMP (реальна історія створення не збережена,
-- але це не критично — лічильник часу гри просто почне рахувати з моменту деплою).
ALTER TABLE saves ADD COLUMN prestige_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE saves ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
UPDATE saves SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
