-- Тір 3: розширення набору молекул (продовження V15). Той самий data-driven
-- принцип (CLAUDE.md, 14.2) — жодного нового Java-коду, лише контент. Усі нові
-- сполуки використовують елементи, вже присутні в грі (H, N, O, F, S — Z<=36).
--
-- bond_energy_ev рахується так само, як у V15: реальна атомізаційна енергія
-- (усі зв'язки розірвано до окремих газоподібних атомів), kJ/mol -> eV (÷96.485).
-- На відміну від V15 (де атомізація взята напряму з довідникових таблиць бондів),
-- тут вона виведена за законом Гесса з двох стандартних (добре задокументованих
-- у будь-якому підручнику загальної хімії) величин:
--   atomization(compound) = Σ ΔHf°(atoms, g) - ΔHf°(compound, g)
-- Стандартні ΔHf° окремих атомів у газовій фазі (kJ/mol): H=218.0, O=249.2,
-- N=472.7, S=278.8, F=79.4. Стандартні ΔHf°(g) сполук (kJ/mol):
-- H2O2=-136.3, SO2=-296.8, SO3=-395.7, H2S=-20.6, NO=+90.25, NO2=+33.2,
-- N2O=+82.05, HF=-273.3.

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('H2O2', 'Пероксид водню', 11.10);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2O2'), id, 2 FROM elements WHERE atomic_number = 1;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2O2'), id, 2 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('SO2', 'Діоксид сульфуру', 11.13);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'SO2'), id, 1 FROM elements WHERE atomic_number = 16;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'SO2'), id, 2 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('SO3', 'Триоксид сульфуру', 14.74);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'SO3'), id, 1 FROM elements WHERE atomic_number = 16;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'SO3'), id, 3 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('H2S', 'Сірководень', 7.62);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2S'), id, 2 FROM elements WHERE atomic_number = 1;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'H2S'), id, 1 FROM elements WHERE atomic_number = 16;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('NO', 'Оксид нітрогену(II)', 6.55);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NO'), id, 1 FROM elements WHERE atomic_number = 7;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NO'), id, 1 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('NO2', 'Оксид нітрогену(IV)', 9.72);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NO2'), id, 1 FROM elements WHERE atomic_number = 7;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'NO2'), id, 2 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('N2O', 'Веселильний газ', 11.53);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'N2O'), id, 2 FROM elements WHERE atomic_number = 7;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'N2O'), id, 1 FROM elements WHERE atomic_number = 8;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('HF', 'Фтороводень', 5.91);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'HF'), id, 1 FROM elements WHERE atomic_number = 1;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'HF'), id, 1 FROM elements WHERE atomic_number = 9;
