package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerUpgradeRepository extends JpaRepository<PlayerUpgrade, Long> {
    List<PlayerUpgrade> findBySaveId(Long saveId);
}