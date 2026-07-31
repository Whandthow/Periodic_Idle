package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerElementRepository extends JpaRepository<PlayerElement, Long> {
    List<PlayerElement> findBySaveId(Long saveId);
}
