-- Тір 3: галогени як молекули (продовження V15/V23). Той самий data-driven
-- принцип — жодного нового Java-коду. Використовує елементи, вже присутні в
-- грі (F, Cl, Br, H; Z<=36).
--
-- На відміну від V23 (Hess's law через ΔHf°), тут bond_energy_ev -- це пряма
-- добре задокументована енергія дисоціації зв'язку (той самий стиль, що й
-- H2/O2/N2 у V15), kJ/mol -> eV (÷96.485):
-- F-F=153, Cl-Cl=243, Br-Br=192, H-Br=366 kJ/mol.

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('F2', 'Флуор', 1.59);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'F2'), id, 2 FROM elements WHERE atomic_number = 9;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('Cl2', 'Хлор', 2.52);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'Cl2'), id, 2 FROM elements WHERE atomic_number = 17;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('Br2', 'Бром', 1.99);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'Br2'), id, 2 FROM elements WHERE atomic_number = 35;

INSERT INTO molecules (formula, name, bond_energy_ev) VALUES ('HBr', 'Бромоводень', 3.79);
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'HBr'), id, 1 FROM elements WHERE atomic_number = 1;
INSERT INTO molecule_components (molecule_id, element_id, atom_count)
SELECT (SELECT id FROM molecules WHERE formula = 'HBr'), id, 1 FROM elements WHERE atomic_number = 35;
