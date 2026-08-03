CREATE TABLE resources (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           code VARCHAR(20) NOT NULL UNIQUE,
                           name VARCHAR(100) NOT NULL,
                           tier INT NOT NULL
);

CREATE TABLE generator (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           code VARCHAR(50) NOT NULL UNIQUE,
                           name VARCHAR(100) NOT NULL,
                           tier INT NOT NULL,
                           cost_resource_id BIGINT NOT NULL,
                           base_cost_number DOUBLE NOT NULL,
                           base_cost_exponent BIGINT NOT NULL,
                           cost_multiplier DOUBLE NOT NULL,
                           FOREIGN KEY (cost_resource_id) REFERENCES resources(id)
);

CREATE TABLE generator_outputs (
                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   generator_id BIGINT NOT NULL,
                                   resource_id BIGINT NOT NULL,
                                   rate_per_level DOUBLE NOT NULL,
                                   FOREIGN KEY (generator_id) REFERENCES generator(id),
                                   FOREIGN KEY (resource_id) REFERENCES resources(id)
);

CREATE TABLE generator_input (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 generator_id BIGINT NOT NULL,
                                 resource_id BIGINT NOT NULL,
                                 rate_per_level DOUBLE NOT NULL,
                                 FOREIGN KEY (generator_id) REFERENCES generator(id),
                                 FOREIGN KEY (resource_id) REFERENCES resources(id)
);