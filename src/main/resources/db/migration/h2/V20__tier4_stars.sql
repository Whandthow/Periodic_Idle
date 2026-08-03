-- Тір 4: Зорі. Головна послідовність — термоядерний синтез Гідрогену в Гелій
-- (реальний proton-proton chain: 4 ¹H -> ⁴He + 2e⁺ + 2ν, ~26-28 МеВ на подію,
-- та сама енергія, що BindingEnergy.totalMeV(2,4) вже рахує для Тіру 2).
-- На відміну від SynthesisService (миттєвий синтез атомів з p/n/e), зоря —
-- пасивний, тривалий процес: споживає вже синтезований Гідроген (Тір 2)
-- і виробляє Гелій з часом, як генератор Тіру 0, але для атомів, не E.

CREATE TABLE stars (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    tier INT NOT NULL,
    base_cost_hydrogen BIGINT NOT NULL,
    cost_multiplier DOUBLE NOT NULL,
    fuel_h_per_event BIGINT NOT NULL,
    output_he_per_event BIGINT NOT NULL,
    events_per_sec_per_level DOUBLE NOT NULL
);

CREATE TABLE player_stars (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id BIGINT NOT NULL,
    star_id BIGINT NOT NULL,
    level INT NOT NULL DEFAULT 0,
    FOREIGN KEY (save_id) REFERENCES saves(id),
    FOREIGN KEY (star_id) REFERENCES stars(id)
);

-- Лічильник вибухів гіпернови (ризик-механіка: нестача Гідрогену -> вибух ->
-- скидає всі player_elements/player_molecules/player_stars цього save).
ALTER TABLE saves ADD COLUMN hypernova_count BIGINT NOT NULL DEFAULT 0;

-- Перший прохід (потребує живого тестування, як і всі попередні тіри):
-- 1 000 000 H на перший рівень, x2.0 подорожчання, 4H->1He за подію (реальний
-- pp-chain), 0.01 подій/сек за рівень (0.04 H/сек, 0.01 He/сек на рівень 1).
INSERT INTO stars (code, name, tier, base_cost_hydrogen, cost_multiplier,
                    fuel_h_per_event, output_he_per_event, events_per_sec_per_level)
VALUES ('main_sequence', 'Зоря головної послідовності', 4,
        1000000, 2.0, 4, 1, 0.01);

-- Тір 4 розблоковується при 1e6+ протонів (той самий data-driven механізм,
-- що й Тіри 2/3 — розділ 7.1/10 CLAUDE.md). Перший прохід: точний поріг за
-- кількістю синтезованого Гідрогену потребував би розширення
-- tier_unlock_conditions на елементи (не лише resources) — свідомо відкладено,
-- протони як proxy достатньо для v1.
INSERT INTO tier_unlock_conditions (tier, resource_id, min_log10)
SELECT 4, id, 6 FROM resources WHERE code = 'p';
