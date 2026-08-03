package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerStarRepository extends JpaRepository<PlayerStar, Long> {
    List<PlayerStar> findBySaveId(Long saveId);
}
