package com.periodic.idle.engine;

import com.periodic.idle.content.Achievement;
import com.periodic.idle.content.AchievementRepository;
import com.periodic.idle.player.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Досягнення: одноразові умови над станом save (енергія/кристали/частинки на порозі,
 * колапси матерії, синтезовані елементи, зламана нескінченність). Перевіряються при
 * кожному опитуванні /api/state (кожні ~1.5с незалежно від активної вкладки), тому
 * навіть короткочасний пік (напр. енергія перед престижем) встигає зафіксуватись.
 */
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerElementRepository playerElementRepository;
    private final SaveRepository saveRepository;

    /** Перевіряє всі ще не розблоковані досягнення і фіксує ті, чия умова вже виконана. */
    @Transactional
    public void checkAndUnlock(Long saveId) {
        List<Achievement> pending = pendingAchievements(saveId);
        if (pending.isEmpty()) return;

        Save save = saveRepository.findById(saveId).orElse(null);
        if (save == null) return;

        List<PlayerResource> resources = playerResourceRepository.findBySaveId(saveId);
        long elementsSynthesized = countElementsSynthesized(saveId);

        for (Achievement a : pending) {
            if (isSatisfied(a, resources, save, elementsSynthesized)) {
                PlayerAchievement pa = new PlayerAchievement();
                pa.setSave(save);
                pa.setAchievement(a);
                pa.setUnlockedAt(LocalDateTime.now());
                playerAchievementRepository.save(pa);
            }
        }
    }

    /** Повний список досягнень з прапором unlocked/unlockedAt для UI. */
    public List<Map<String, Object>> listWithStatus(Long saveId) {
        Map<Long, LocalDateTime> unlockedAt = playerAchievementRepository.findBySaveId(saveId).stream()
                .collect(Collectors.toMap(pa -> pa.getAchievement().getId(), PlayerAchievement::getUnlockedAt));

        return achievementRepository.findAll().stream()
                .map(a -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", a.getCode());
                    map.put("name", a.getName());
                    map.put("description", a.getDescription());
                    LocalDateTime at = unlockedAt.get(a.getId());
                    map.put("unlocked", at != null);
                    map.put("unlockedAt", at);
                    return map;
                })
                .toList();
    }

    private List<Achievement> pendingAchievements(Long saveId) {
        List<Achievement> all = achievementRepository.findAll();
        if (all.isEmpty()) return List.of();
        Set<Long> unlockedIds = playerAchievementRepository.findBySaveId(saveId).stream()
                .map(pa -> pa.getAchievement().getId())
                .collect(Collectors.toSet());
        return all.stream().filter(a -> !unlockedIds.contains(a.getId())).toList();
    }

    private long countElementsSynthesized(Long saveId) {
        return playerElementRepository.findBySaveId(saveId).stream()
                .filter(pe -> pe.getCount() > 0)
                .count();
    }

    private boolean isSatisfied(Achievement a, List<PlayerResource> resources, Save save,
                                 long elementsSynthesized) {
        return switch (a.getConditionType()) {
            case "RESOURCE_LOG10" -> resourceLog10Satisfied(a, resources);
            case "MATTER_COLLAPSES" -> save.getMatterCollapses() >= a.getThreshold();
            case "ELEMENTS_SYNTHESIZED" -> elementsSynthesized >= a.getThreshold();
            case "BROKEN_INFINITY" -> save.isBrokenInfinity();
            default -> false;
        };
    }

    private boolean resourceLog10Satisfied(Achievement a, List<PlayerResource> resources) {
        if (a.getResource() == null) return false;
        PlayerResource pr = resources.stream()
                .filter(r -> r.getResource() != null && r.getResource().getId().equals(a.getResource().getId()))
                .findFirst()
                .orElse(null);
        if (pr == null) return false;
        double mantissa = pr.getNumber();
        if (!Double.isFinite(mantissa) || mantissa <= 0) return false;
        double log10 = Math.log10(mantissa) + pr.getExponent();
        return log10 >= a.getThreshold();
    }
}
