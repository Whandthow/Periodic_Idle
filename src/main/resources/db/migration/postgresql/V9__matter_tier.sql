-- Тір 1: Матерія (Matter)
-- Додаємо три субатомні ресурси: протон, нейтрон, електрон.
-- Вони отримуються через "Розщеплення кристала" (обмін VC -> p+n+e).

INSERT INTO resources (code, name, tier) VALUES
    ('p', 'Протон',   1),
    ('n', 'Нейтрон',  1),
    ('e', 'Електрон', 1);

-- Ресурс у гравця (save_id=1). exponent=0, number=0.
INSERT INTO player_resources (save_id, resource_id, number, exponent)
SELECT 1, id, 0, 0 FROM resources WHERE code IN ('p', 'n', 'e');
