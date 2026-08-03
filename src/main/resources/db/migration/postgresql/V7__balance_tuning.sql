-- Balance tuning: плавніша крива генераторів, дешевше Ядро, сильніші мультиплікатори.
-- Мета: перша prestige за 5-10 хв від 0 до 1e9 енергії (V-поріг знижено в Java).

-- ============ ГЕНЕРАТОРИ ============
-- Порядок: (id, base_cost_number, base_cost_exponent, cost_multiplier, rate_per_level)
-- Було: 10/0.5/1.5, 100/3/1.5, 1e3/15/1.6, 1e4/80/1.7, 1e5/500/1.8, 1e6/3000/2.0
-- Стало: плавніший множник + x10-прогресія швидкостей.

UPDATE generator SET base_cost_number=1.0, base_cost_exponent=1, cost_multiplier=1.15 WHERE id=1;  -- void_gen
UPDATE generator SET base_cost_number=1.0, base_cost_exponent=2, cost_multiplier=1.20 WHERE id=2;  -- quantum_loop
UPDATE generator SET base_cost_number=1.0, base_cost_exponent=3, cost_multiplier=1.25 WHERE id=3;  -- vacuum_resonator
UPDATE generator SET base_cost_number=1.0, base_cost_exponent=4, cost_multiplier=1.30 WHERE id=4;  -- dark_condenser
UPDATE generator SET base_cost_number=1.0, base_cost_exponent=5, cost_multiplier=1.35 WHERE id=5;  -- entropy_engine
UPDATE generator SET base_cost_number=1.0, base_cost_exponent=6, cost_multiplier=1.40 WHERE id=6;  -- singularity_core

UPDATE generator_outputs SET rate_per_level=1.0     WHERE generator_id=1;
UPDATE generator_outputs SET rate_per_level=10.0    WHERE generator_id=2;
UPDATE generator_outputs SET rate_per_level=100.0   WHERE generator_id=3;
UPDATE generator_outputs SET rate_per_level=1000.0  WHERE generator_id=4;
UPDATE generator_outputs SET rate_per_level=10000.0 WHERE generator_id=5;
UPDATE generator_outputs SET rate_per_level=100000.0 WHERE generator_id=6;

-- ============ АПГРЕЙДИ ============
-- Ядро: м'якший ріст ціни + сильніший ефект за рівень (0.10 -> 0.15)
UPDATE upgrades SET cost_multiplier=3.0, effect_value=0.15 WHERE code='core';

-- ENERGY_MULT: ефект 0.5 -> 0.30, ціна множник 3 -> 2
UPDATE upgrades SET effect_value=0.30, cost_multiplier=2.0 WHERE code='upg_energy_mult';

-- GEN_SPECIFIC_MULT: ціна множник 3 -> 2
UPDATE upgrades SET cost_multiplier=2.0 WHERE code='upg_gen_specific';

-- CRYSTAL_GAIN: подорожчання 3 -> 2.5
UPDATE upgrades SET cost_multiplier=2.5 WHERE code='upg_crystal_gain';

-- AUTOBUY: базова ціна (1e4) висока — лишаємо, але множник 5 -> 3
UPDATE upgrades SET cost_multiplier=3.0 WHERE code='upg_autobuy';

-- ENERGY_POW: ефект 0.02 -> 0.05 (щоб помітно прискорювало великі числа)
UPDATE upgrades SET effect_value=0.05, cost_multiplier=3.0 WHERE code='upg_energy_pow';

-- PHANTOM_GEN: лишаємо, але подорожчання 5 -> 3
UPDATE upgrades SET cost_multiplier=3.0 WHERE code='upg_phantom';

-- COST_SCALE_REDUCE: ефект 0.01 -> 0.02 (швидше виходить на підлогу 1.03)
UPDATE upgrades SET effect_value=0.02, cost_multiplier=2.5 WHERE code='upg_cost_scale';

-- GEN_STACK: подорожчання 3 -> 2.5
UPDATE upgrades SET cost_multiplier=2.5 WHERE code='upg_gen_stack';
