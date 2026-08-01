package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, Long> {
    List<PlayerAchievement> findBySaveId(Long saveId);
}
