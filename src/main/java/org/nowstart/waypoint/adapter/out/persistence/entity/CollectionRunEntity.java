package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;

@Entity
@Table(name = "collection_runs")
public class CollectionRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_type", nullable = false, length = 50)
    private CollectionApiType apiType;

    @Column(name = "request_key")
    private String requestKey;

    @Lob
    @Column(name = "request_params_json")
    private String requestParamsJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CollectionStatus status;

    @Column(name = "http_status")
    private int httpStatus;

    @Column(name = "result_code", length = 80)
    private String resultCode;

    @Column(name = "result_message")
    private String resultMessage;

    @Column(name = "row_count")
    private int rowCount;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    protected CollectionRunEntity() {
    }

    public CollectionRunEntity(CollectionApiType apiType, String requestKey, String requestParamsJson) {
        this.apiType = apiType;
        this.requestKey = requestKey;
        this.requestParamsJson = requestParamsJson;
        this.startedAt = Instant.now();
        this.status = CollectionStatus.FAILED;
    }

    public void finish(
            CollectionStatus status,
            int httpStatus,
            String resultCode,
            String resultMessage,
            int rowCount,
            String errorMessage
    ) {
        this.status = status;
        this.httpStatus = httpStatus;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.rowCount = rowCount;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CollectionApiType getApiType() {
        return apiType;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public CollectionStatus getStatus() {
        return status;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
