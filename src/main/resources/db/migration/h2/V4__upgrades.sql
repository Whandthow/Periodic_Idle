CREATE TABLE upgrades (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          code VARCHAR(50) NOT NULL UNIQUE,
                          name VARCHAR(100) NOT NULL,
                          description VARCHAR(255),
                          tier INT NOT NULL,
                          effect_type VARCHAR(50) NOT NULL,
                          effect_value DOUBLE NOT NULL DEFAULT 0,
                          target_generator_id BIGINT,
                          cost_resource_id BIGINT NOT NULL,
                          cost_number DOUBLE NOT NULL,
                          cost_exponent BIGINT NOT NULL,
                          cost_multiplier DOUBLE NOT NULL DEFAULT 2.0,
                          max_level INT NOT NULL DEFAULT 1,
                          required_upgrade_id BIGINT,
                          FOREIGN KEY (target_generator_id) REFERENCES generator(id),
                          FOREIGN KEY (cost_resource_id) REFERENCES resources(id),
                          FOREIGN KEY (required_upgrade_id) REFERENCES upgrades(id)
);

CREATE TABLE player_upgrades (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 save_id BIGINT NOT NULL,
                                 upgrade_id BIGINT NOT NULL,
                                 level INT NOT NULL DEFAULT 0,
                                 FOREIGN KEY (save_id) REFERENCES saves(id),
                                 FOREIGN KEY (upgrade_id) REFERENCES upgrades(id)
);

-- Перші апгрейди Tier 0
INSERT INTO upgrades (code, name, description, tier, effect_type, effect_value, cost_resource_id, cost_number, cost_exponent, cost_multiplier, max_level)
VALUES
    ('energy_boost_1', 'Квантове посилення', '+50% до енергії', 0, 'ENERGY_MULT', 0.5, 1, 1.0, 2, 2.5, 10),
    ('energy_boost_2', 'Вакуумне резонування', '+100% до енергії', 0, 'ENERGY_MULT', 1.0, 1, 1.0, 4, 3.0, 5),
    ('gen_boost_1', 'Оптимізація генератора', '+30% до генератора пустоти', 0, 'GENERATOR_MULT', 0.3, 1, 5.0, 1, 2.0, 20),
    ('gen_cost_down', 'Ефективність', '-10% вартості генераторів', 0, 'COST_DISCOUNT', 0.1, 1, 1.0, 3, 2.5, 10),
    ('collapse_boost', 'Ехо резонанс', '+1 кристал за колапс', 0, 'COLLAPSE_BONUS', 1.0, 1, 1.0, 5, 4.0, 5);