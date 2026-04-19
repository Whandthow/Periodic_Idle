INSERT INTO resources (code, name, tier) VALUES ('E', 'Енергія', 0);

INSERT INTO generator (code, name, tier, cost_resource_id, base_cost_number, base_cost_exponent, cost_multiplier)
VALUES ('void_gen', 'Генератор пустоти', 0, 1, 1.0, 1, 1.5);

INSERT INTO generator_outputs (generator_id, resource_id, rate_per_level)
VALUES (1, 1, 0.5);