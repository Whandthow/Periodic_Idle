package com.periodic.idle.player;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SaveRepository extends JpaRepository<Save, Long> {
}