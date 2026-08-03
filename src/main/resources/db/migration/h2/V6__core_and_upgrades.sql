-- Додаємо колонку відкриття через тір Ядра
ALTER TABLE upgrades ADD COLUMN unlock_core_tier INT NOT NULL DEFAULT 0;

-- Очищаємо попередні апгрейди
DELETE FROM player_upgrades;
DELETE FROM upgrades;

-- Ресурс: Кристал Пустоти (prestige-валюта)
INSERT INTO resources (code, name, tier) VALUES ('VC', 'Кристал пустоти', 0);

-- Ресурс у гравця
INSERT INTO player_resources (save_id, resource_id, number, exponent)
SELECT 1, id, 0, 0 FROM resources WHERE code = 'VC';

-- 9 апгрейдів Tier 0: центр (ядро) + 8 по променях
-- effect_value — базовий коефіцієнт ефекту, cost_* — ціна першого рівня
INSERT INTO upgrades
    (code, name, description, tier, effect_type, effect_value,
     cost_resource_id, cost_number, cost_exponent, cost_multiplier,
     max_level, unlock_core_tier)
VALUES
    -- Центр: Ядро. Саме воно відкриває інші. Ефект: глобальний буст від кристалів.
    ('core', 'Ядро Пустоти',
     'Відкриває нові покращення. Підсилює все залежно від кристалів пустоти.',
     0, 'CORE', 0.10,
     1, 10.0, 0, 5.0,
     999, 0),

    -- Відкривається Ядром T1
    ('upg_energy_mult', 'Підсилення Енергії',
     'Множник до загального виробництва енергії (софткап після 20-го тіру).',
     0, 'ENERGY_MULT', 0.50,
     1, 50.0, 0, 3.0,
     999, 1),

    ('upg_gen_specific', 'Буст Генераторів',
     'Кожен тір бустить новий генератор і посилює попередні.',
     0, 'GEN_SPECIFIC_MULT', 0.30,
     1, 1.0, 2, 3.0,
     999, 1),

    -- Ядро T2
    ('upg_crystal_gain', 'Кристалізація',
     'Збільшує отримання кристалів пустоти.',
     0, 'CRYSTAL_GAIN', 0.25,
     1, 1.0, 3, 3.0,
     999, 2),

    ('upg_autobuy', 'Автоматизація',
     'Автокупівля генераторів. Тір — кількість автоматизованих.',
     0, 'AUTOBUY', 1.0,
     1, 1.0, 4, 5.0,
     6, 2),

    -- Ядро T3
    ('upg_energy_pow', 'Степінь Енергії',
     'Підносить виробництво енергії у степінь.',
     0, 'ENERGY_POW', 0.02,
     1, 1.0, 5, 5.0,
     999, 3),

    ('upg_phantom', 'Фантоми',
     'Генератори створюють фантомів, які виробляють тільки енергію.',
     0, 'PHANTOM_GEN', 1.0,
     1, 1.0, 6, 5.0,
     3, 3),

    -- Ядро T4
    ('upg_cost_scale', 'Згладження Ціни',
     'Зменшує коефіцієнт подорожчання генераторів.',
     0, 'COST_SCALE_REDUCE', 0.01,
     1, 1.0, 7, 3.0,
     999, 4),

    ('upg_gen_stack', 'Стек Купівлі',
     'Кожна купівля генератора додає до його множника.',
     0, 'GEN_STACK', 1.0,
     1, 1.0, 8, 3.0,
     999, 4);
