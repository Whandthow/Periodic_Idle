package com.periodic.idle.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TierUnlockConditionRepository extends JpaRepository<TierUnlockCondition, Long> {
    List<TierUnlockCondition> findAllByOrderByTierAsc();
}
