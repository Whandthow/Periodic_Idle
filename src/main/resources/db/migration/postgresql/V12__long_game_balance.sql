-- V12: гра розтягується на години/дні гри замість 5-10хв до першого престижу.
-- Поріг престижу і дільник перенесено в Java (PrestigeService: 9->25, 2->3).
-- Тут — стрімкіше подорожчання генераторів і апгрейдів, і суттєво дорожче Ядро,
-- щоб рання гра не "вибухала" за лічені хвилини. Див. docs/balance.md.

-- ============ ГЕНЕРАТОРИ: стрімкіший cost_multiplier ============
UPDATE generator SET cost_multiplier=1.24 WHERE id=1;  -- void_gen        (було 1.15)
UPDATE generator SET cost_multiplier=1.30 WHERE id=2;  -- quantum_loop    (було 1.20)
UPDATE generator SET cost_multiplier=1.38 WHERE id=3;  -- vacuum_resonator(було 1.25)
UPDATE generator SET cost_multiplier=1.46 WHERE id=4;  -- dark_condenser  (було 1.30)
UPDATE generator SET cost_multiplier=1.55 WHERE id=5;  -- entropy_engine  (було 1.35)
UPDATE generator SET cost_multiplier=1.65 WHERE id=6;  -- singularity_core(було 1.40)

-- ============ ЯДРО: набагато дорожча база + стрімкіший ріст ============
-- Було: cost_number=10.0 (10 E), cost_multiplier=3.0, effect_value=0.15
UPDATE upgrades SET cost_number=1.0, cost_exponent=4, cost_multiplier=4.0, effect_value=0.10
    WHERE code='core';

-- ============ РЕШТА АПГРЕЙДІВ: стрімкіший cost_multiplier ============
UPDATE upgrades SET cost_multiplier=2.6 WHERE code='upg_energy_mult';   -- було 2.0
UPDATE upgrades SET cost_multiplier=2.6 WHERE code='upg_gen_specific';  -- було 2.0
UPDATE upgrades SET cost_multiplier=3.2 WHERE code='upg_crystal_gain';  -- було 2.5
UPDATE upgrades SET cost_multiplier=3.4 WHERE code='upg_autobuy';       -- було 3.0
UPDATE upgrades SET cost_multiplier=3.6 WHERE code='upg_energy_pow';    -- було 3.0
UPDATE upgrades SET cost_multiplier=3.6 WHERE code='upg_phantom';       -- було 3.0
UPDATE upgrades SET cost_multiplier=3.2 WHERE code='upg_cost_scale';    -- було 2.5
UPDATE upgrades SET cost_multiplier=3.2 WHERE code='upg_gen_stack';     -- було 2.5
