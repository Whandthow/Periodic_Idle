-- V17: живе тестування V12-балансу (бот-симуляція через реальний застосунок,
-- прискорений tick-speed) показало, що перша реінкарнація фактично вимагала
-- ~120 БЕЗПЕРЕРВНИХ ігрових днів виробництва замість задокументованих 1-3 годин
-- активної гри — cost_multiplier генераторів/апгрейдів V12 виявився на порядки
-- крутішим, ніж треба для довгої, але досяжної гри. Пом'якшуємо криву помітно
-- нижче навіть V7 (де поріг престижу був 1e9, а не сьогоднішній 1e25 — набагато
-- вищий поріг сам по собі вже подовжує гру, тож крива подорожчання може бути
-- м'якшою, ніж у V7, і разом усе одно дати довшу гру, ніж V7).

-- ============ ГЕНЕРАТОРИ: помітно пологіший cost_multiplier ============
UPDATE generator SET cost_multiplier=1.10 WHERE id=1;  -- void_gen        (було 1.24)
UPDATE generator SET cost_multiplier=1.13 WHERE id=2;  -- quantum_loop    (було 1.30)
UPDATE generator SET cost_multiplier=1.16 WHERE id=3;  -- vacuum_resonator(було 1.38)
UPDATE generator SET cost_multiplier=1.20 WHERE id=4;  -- dark_condenser  (було 1.46)
UPDATE generator SET cost_multiplier=1.24 WHERE id=5;  -- entropy_engine  (було 1.55)
UPDATE generator SET cost_multiplier=1.28 WHERE id=6;  -- singularity_core(було 1.65)

-- ============ ЯДРО: дешевша база + пологіший ріст ============
-- Було: cost_number=1.0, cost_exponent=4 (10 000 E), cost_multiplier=4.0.
-- CORE не дає ЖОДНОГО ефекту без Кристалів Пустоти (буст залежить від log10(VC)),
-- тож дорога база лише спокушає "злити" бюджет до першого престижу без користі —
-- пологіший ріст робить це менш катастрофічним, якщо гравець все ж купить рано.
UPDATE upgrades SET cost_number=1.0, cost_exponent=3, cost_multiplier=2.2
    WHERE code='core';

-- ============ РЕШТА АПГРЕЙДІВ: помітно пологіший cost_multiplier ============
UPDATE upgrades SET cost_multiplier=1.7 WHERE code='upg_energy_mult';   -- було 2.6
UPDATE upgrades SET cost_multiplier=1.7 WHERE code='upg_gen_specific';  -- було 2.6
UPDATE upgrades SET cost_multiplier=2.0 WHERE code='upg_crystal_gain';  -- було 3.2
UPDATE upgrades SET cost_multiplier=2.1 WHERE code='upg_autobuy';       -- було 3.4
UPDATE upgrades SET cost_multiplier=2.2 WHERE code='upg_energy_pow';    -- було 3.6
UPDATE upgrades SET cost_multiplier=2.2 WHERE code='upg_phantom';       -- було 3.6
UPDATE upgrades SET cost_multiplier=2.0 WHERE code='upg_cost_scale';    -- було 3.2
UPDATE upgrades SET cost_multiplier=2.0 WHERE code='upg_gen_stack';     -- було 3.2
