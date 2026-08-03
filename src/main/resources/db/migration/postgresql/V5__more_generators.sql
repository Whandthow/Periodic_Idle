 -- Генератори Tier 0 (id 2-6, id 1 вже є — Генератор пустоти)
INSERT INTO generator (code, name, tier, cost_resource_id, base_cost_number, base_cost_exponent, cost_multiplier)
VALUES
    ('quantum_loop',    'Квантова петля',       0, 1, 1.0, 2, 1.5),
    ('vacuum_resonator','Вакуумний резонатор',   0, 1, 1.0, 3, 1.6),
    ('dark_condenser',  'Темний конденсатор',    0, 1, 1.0, 4, 1.7),
    ('entropy_engine',  'Ентропійний двигун',    0, 1, 1.0, 5, 1.8),
    ('singularity_core','Ядро сингулярності',    0, 1, 1.0, 6, 2.0);

-- Виходи генераторів (всі виробляють енергію, resource_id=1)
INSERT INTO generator_outputs (generator_id, resource_id, rate_per_level)
VALUES
    (2, 1, 3.0),
    (3, 1, 15.0),
    (4, 1, 80.0),
    (5, 1, 500.0),
    (6, 1, 3000.0);
