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
        String resultMessage = exception.getMessage();
        if (exception instanceof TagoApiException tagoException) {
            httpStatus = tagoException.getHttpStatus();
            resultCode = tagoException.getResultCode();
            resultMessage = tagoException.getResultMessage();
        }
        saveTransitDataPort.finishCollectionRun(
                run.runId(),
                CollectionStatus.FAILED,
                httpStatus,
                resultCode,
                resultMessage,
                0,
                exception.getMessage()
        );
        return new CollectionResult(
                run.apiType(),
                CollectionStatus.FAILED,
                0,
                1,
                exception.getMessage(),
                run.startedAt(),
                Instant.now()
        );
    }

    record CollectionRun(
            Long runId,
            CollectionApiType apiType,
            Instant startedAt
    ) {
    }
}
