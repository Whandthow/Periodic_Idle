-- Хардкап Степені Енергії: надто потужна при високих рівнях (1.05^N летить у нескінченність).
-- Обмежуємо до max_level = 8. Також обрізаємо існуючі рівні гравця, якщо є.

UPDATE upgrades SET max_level = 8 WHERE code = 'upg_energy_pow';

UPDATE player_upgrades
   SET level = 8
 WHERE level > 8
   AND upgrade_id = (SELECT id FROM upgrades WHERE code = 'upg_energy_pow');
