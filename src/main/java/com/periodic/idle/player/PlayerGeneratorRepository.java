package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerGeneratorRepository extends JpaRepository<PlayerGenerator, Long> {
    List<PlayerGenerator> findBySaveId(Long saveId);
}