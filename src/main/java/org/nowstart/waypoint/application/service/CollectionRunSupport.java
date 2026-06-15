package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class CollectionRunSupport {

    private final SaveTransitDataPort saveTransitDataPort;

    CollectionRunSupport(SaveTransitDataPort saveTransitDataPort) {
        this.saveTransitDataPort = saveTransitDataPort;
    }

    CollectionRun start(CollectionApiType apiType, String requestKey, String requestParamsJson) {
        Instant startedAt = Instant.now();
        Long runId = saveTransitDataPort.startCollectionRun(apiType, requestKey, requestParamsJson);
        return new CollectionRun(runId, apiType, startedAt);
    }

    CollectionResult finish(
            CollectionRun run,
            CollectionStatus status,
            int rowCount,
            int failureCount,
            String message
    ) {
        saveTransitDataPort.finishCollectionRun(run.runId(), status, 200, "OK", message, rowCount, null);
        return new CollectionResult(run.apiType(), status, rowCount, failureCount, message, run.startedAt(), Instant.now());
    }

    CollectionResult fail(CollectionRun run, RuntimeException exception) {
        int httpStatus = 0;
        String resultCode = "FAILED";
        String resultMessage = sanitize(exception.getMessage());
        if (exception instanceof TagoApiException tagoException) {
            httpStatus = tagoException.getHttpStatus();
            resultCode = tagoException.getResultCode();
            resultMessage = sanitize(tagoException.getResultMessage());
        }
        String message = failureMessage(exception, resultCode, resultMessage);
        saveTransitDataPort.finishCollectionRun(
                run.runId(),
                CollectionStatus.FAILED,
                httpStatus,
                resultCode,
                resultMessage,
                0,
                message
        );
        return new CollectionResult(
                run.apiType(),
                CollectionStatus.FAILED,
                0,
                1,
                message,
                run.startedAt(),
                Instant.now()
        );
    }

    private static String failureMessage(RuntimeException exception, String resultCode, String resultMessage) {
        if (exception instanceof TagoApiException) {
            return "TAGO request failed. resultCode=" + resultCode + ", resultMessage=" + resultMessage;
        }
        return sanitize(exception.getMessage());
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)(serviceKey=)[^&\\s]+", "$1***");
    }

    record CollectionRun(
            Long runId,
            CollectionApiType apiType,
            Instant startedAt
    ) {
    }
}
