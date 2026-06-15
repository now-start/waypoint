package org.nowstart.waypoint.application.service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
final class ConcurrentCollectionSupport {

    private ConcurrentCollectionSupport() {
    }

    static <S, T> List<TaskResult<S, T>> execute(
            String taskName,
            List<S> sources,
            int concurrency,
            Function<S, T> task,
            Function<S, T> fallback
    ) {
        if (sources.isEmpty()) {
            return List.of();
        }

        int effectiveConcurrency = Math.min(Math.max(1, concurrency), sources.size());
        long startedAt = System.nanoTime();
        log.info(
                "Starting parallel collection tasks. taskName={}, totalTasks={}, requestedConcurrency={}, effectiveConcurrency={}",
                taskName,
                sources.size(),
                concurrency,
                effectiveConcurrency
        );

        if (effectiveConcurrency == 1) {
            List<TaskResult<S, T>> results = sources.stream()
                    .map(source -> executeOne(source, task, fallback))
                    .toList();
            logFinished(taskName, results, startedAt);
            return results;
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
            logFinished(taskName, results, startedAt);
            return results;
        }
    }

    private static <S, T> TaskResult<S, T> executeOne(
            S source,
            Function<S, T> task,
            Function<S, T> fallback
    ) {
        try {
            T value = task.apply(source);
            return new TaskResult<>(source, value, null);
        } catch (RuntimeException ex) {
            return new TaskResult<>(source, fallback.apply(source), ex);
        }
    }

    private static <S, T> void logFinished(String taskName, List<TaskResult<S, T>> results, long startedAt) {
        long failureCount = results.stream()
                .filter(TaskResult::failed)
                .count();
        log.info(
                "Finished parallel collection tasks. taskName={}, totalTasks={}, failures={}, durationMs={}",
                taskName,
                results.size(),
                failureCount,
                elapsedMillis(startedAt)
        );
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
