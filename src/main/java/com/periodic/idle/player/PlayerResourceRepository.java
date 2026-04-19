package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerResourceRepository extends JpaRepository<PlayerResource, Long> {
    List<PlayerResource> findBySaveId(Long saveId);
}