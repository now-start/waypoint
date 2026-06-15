package org.nowstart.waypoint.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

final class ConcurrentCollectionSupport {

    private ConcurrentCollectionSupport() {
    }

    static <S, T> List<TaskResult<S, T>> execute(
            List<S> sources,
            int concurrency,
            Function<S, T> task,
            Function<S, T> fallback
    ) {
        if (sources.isEmpty()) {
            return List.of();
        }

        int effectiveConcurrency = Math.min(Math.max(1, concurrency), sources.size());
        if (effectiveConcurrency == 1) {
            return sources.stream()
                    .map(source -> executeOne(source, task, fallback))
                    .toList();
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(effectiveConcurrency)) {
            List<CompletableFuture<TaskResult<S, T>>> futures = sources.stream()
                    .map(source -> CompletableFuture.supplyAsync(
                            () -> executeOne(source, task, fallback),
                            executor
                    ))
                    .toList();
            List<TaskResult<S, T>> results = new ArrayList<>(sources.size());
            for (CompletableFuture<TaskResult<S, T>> future : futures) {
                results.add(future.join());
            }
            return results;
        }
    }

    private static <S, T> TaskResult<S, T> executeOne(
            S source,
            Function<S, T> task,
            Function<S, T> fallback
    ) {
        try {
            return new TaskResult<>(source, task.apply(source), null);
        } catch (RuntimeException ex) {
            return new TaskResult<>(source, fallback.apply(source), ex);
        }
    }

    record TaskResult<S, T>(
            S source,
            T value,
            RuntimeException failure
    ) {

        boolean failed() {
            return failure != null;
        }
    }
}
