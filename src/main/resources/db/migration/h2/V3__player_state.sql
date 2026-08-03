CREATE TABLE saves (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       player_name VARCHAR(50) NOT NULL,
                       last_tick TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE player_resources (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  save_id BIGINT NOT NULL,
                                  resource_id BIGINT NOT NULL,
                                  number DOUBLE NOT NULL DEFAULT 0,
                                  exponent BIGINT NOT NULL DEFAULT 0,
                                  FOREIGN KEY (save_id) REFERENCES saves(id),
                                  FOREIGN KEY (resource_id) REFERENCES resources(id)
);

CREATE TABLE player_generators (
                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   save_id BIGINT NOT NULL,
                                   generator_id BIGINT NOT NULL,
                                   level INT NOT NULL DEFAULT 0,
                                   FOREIGN KEY (save_id) REFERENCES saves(id),
                                   FOREIGN KEY (generator_id) REFERENCES generator(id)
);

INSERT INTO saves (player_name) VALUES ('dev');
INSERT INTO player_resources (save_id, resource_id, number, exponent) VALUES (1, 1, 0, 0);
INSERT INTO player_generators (save_id, generator_id, level) VALUES (1, 1, 1);