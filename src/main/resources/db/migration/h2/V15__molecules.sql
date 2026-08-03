-- Тір 3: Молекули. Продовження наукової концепції (CLAUDE.md, розділ 1) —
-- хімічні зв'язки з уже синтезованих атомів. bond_energy_ev — реальна сумарна
-- (атомізаційна) енергія зв'язку в електрон-вольтах: на ~6 порядків менша за
-- ядерну енергію зв'язку (МеВ) — так само, як хімія на порядки слабша за
-- ядерну фізику в реальному світі (MoleculeService переводить eV -> MeV).

CREATE TABLE molecules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    formula VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    bond_energy_ev DOUBLE NOT NULL
);

CREATE TABLE molecule_components (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    molecule_id BIGINT NOT NULL,
    element_id BIGINT NOT NULL,
    atom_count INT NOT NULL,
    FOREIGN KEY (molecule_id) REFERENCES molecules(id),
    FOREIGN KEY (element_id) REFERENCES elements(id)
);

CREATE TABLE player_molecules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id BIGINT NOT NULL,
    molecule_id BIGINT NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (save_id) REFERENCES saves(id),
    FOREIGN KEY (molecule_id) REFERENCES molecules(id)
);

-- Реальні атомізаційні/дисоціаційні енергії зв'язку (загальновідомі довідникові
-- значення, kJ/mol переведено в eV діленням на 96.485):
-- H2=436, O2=498, N2=945, H2O=917.8, CO2=1608.5, CH4=1662, NH3=1172, CO=1071.9,
-- HCl=431.9, NaCl=412 kJ/mol.

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('H2', 'Водень', 4.52);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2'), id, 2 FROM elements WHERE atomic_number = 1;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('O2', 'Кисень', 5.16);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'O2'), id, 2 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('N2', 'Азот', 9.79);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'N2'), id, 2 FROM elements WHERE atomic_number = 7;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('H2O', 'Вода', 9.51);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2O'), id, 2 FROM elements WHERE atomic_number = 1;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2O'), id, 1 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('CO2', 'Вуглекислий газ', 16.67);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'CO2'), id, 1 FROM elements WHERE atomic_number = 6;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'CO2'), id, 2 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('CH4', 'Метан', 17.23);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'CH4'), id, 1 FROM elements WHERE atomic_number = 6;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'CH4'), id, 4 FROM elements WHERE atomic_number = 1;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('NH3', 'Аміак', 12.15);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NH3'), id, 1 FROM elements WHERE atomic_number = 7;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NH3'), id, 3 FROM elements WHERE atomic_number = 1;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('CO', 'Чадний газ', 11.11);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'CO'), id, 1 FROM elements WHERE atomic_number = 6;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'CO'), id, 1 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('HCl', 'Хлороводень', 4.48);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'HCl'), id, 1 FROM elements WHERE atomic_number = 1;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'HCl'), id, 1 FROM elements WHERE atomic_number = 17;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('NaCl', 'Кухонна сіль', 4.27);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NaCl'), id, 1 FROM elements WHERE atomic_number = 11;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NaCl'), id, 1 FROM elements WHERE atomic_number = 17;

-- Тір 3 (Молекули) розблоковується, коли гравець вже має суттєвий запас протонів
-- (той самий data-driven механізм, що й Тір 2 — див. tier_unlock_conditions V13).
INSERT INTO tier_unlock_conditions (tier, resource_id, min_log10)
SELECT 3, id, 4 FROM resources WHERE code = 'p';
