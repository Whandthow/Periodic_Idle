package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SaveRepository extends JpaRepository<Save, Long> {
    Optional<Save> findByClientToken(String clientToken);
}