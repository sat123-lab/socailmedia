package com.example.demo.recommendation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring-discovered registry of {@link RankingModel} beans + an override
 * hook for runtime swap-in (e.g. plug an ML-served scorer at startup).
 *
 * <p>Defaults to the highest-priority bean Spring found. If you
 * register a model at runtime via {@link #setActive(RankingModel)} it
 * wins until cleared.</p>
 */
@Component
@Slf4j
public class ModelRegistry {

    private final List<RankingModel> discovered;
    private final AtomicReference<RankingModel> override = new AtomicReference<>();

    @Autowired
    public ModelRegistry(List<RankingModel> beans) {
        this.discovered = beans.stream()
                .sorted(Comparator.comparingInt(RankingModel::priority).reversed())
                .toList();
        log.info("RankingModel beans discovered: {}",
                discovered.stream().map(RankingModel::id).toList());
    }

    /** Returns the currently-active ranker. Never null — the heuristic
     *  base model is always available as a final fallback. */
    public RankingModel active() {
        RankingModel m = override.get();
        return m != null ? m : discovered.get(0);
    }

    /** Pin an override (e.g. an ML-served scorer registered at startup
     *  by a plugin module). Pass {@code null} to clear. */
    public void setActive(RankingModel m) {
        override.set(m);
        log.info("RankingModel override set to {}", m == null ? "<cleared>" : m.id());
    }

    public List<String> available() {
        return discovered.stream().map(RankingModel::id).toList();
    }
}
