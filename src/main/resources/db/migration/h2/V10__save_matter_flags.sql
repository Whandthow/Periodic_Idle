-- Тір 1: прапори "Колапсу матерії" на save, яких бракувало в схемі
-- (Save.java вже очікував ці колонки, але жодна міграція їх не додавала).

ALTER TABLE saves ADD COLUMN broken_infinity BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE saves ADD COLUMN matter_collapses BIGINT NOT NULL DEFAULT 0;
ALTER TABLE saves ADD COLUMN autobuy_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE saves ADD COLUMN client_token VARCHAR(64);
ALTER TABLE saves ADD CONSTRAINT uq_saves_client_token UNIQUE (client_token);
