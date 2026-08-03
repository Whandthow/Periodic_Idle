-- Автопокупка апгрейдів Тіру 0 (окремо від автопокупки генераторів). Розблоковується
-- після MatterService.AUTO_UPGRADE_UNLOCK_COLLAPSES (4) колапсів матерії — до того часу
-- перемикач у UI прихований. Вимкнено за замовчуванням, як і auto_synthesize_enabled.
ALTER TABLE saves ADD COLUMN auto_upgrade_enabled BOOLEAN NOT NULL DEFAULT FALSE;
