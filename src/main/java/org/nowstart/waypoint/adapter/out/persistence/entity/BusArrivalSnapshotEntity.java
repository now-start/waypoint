package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "bus_arrival_snapshots",
        indexes = {
                @Index(name = "ix_arrival_stop_collected", columnList = "city_code,source_node_id,collected_at"),
                @Index(name = "ix_arrival_route_collected", columnList = "city_code,source_route_id,collected_at"),
                @Index(name = "ix_arrival_city_collected_id", columnList = "city_code,collected_at,id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusArrivalSnapshotEntity implements Persistable<String> {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "city_code", nullable = false, length = 20)
    private String cityCode;

    @Column(name = "source_node_id", nullable = false, length = 80)
    private String sourceNodeId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "source_route_id", length = 80)
    private String sourceRouteId;

    @Column(name = "route_no", length = 80)
    private String routeNo;

    @Column(name = "route_type", length = 80)
    private String routeType;

    @Column(name = "arrival_remaining_station_count")
    private Integer arrivalRemainingStationCount;

    @Column(name = "arrival_remaining_minutes")
    private Integer arrivalRemainingMinutes;

    @Column(name = "arrival_expected_at")
    private Instant arrivalExpectedAt;

    @Column(name = "vehicle_type", length = 80)
    private String vehicleType;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    public BusArrivalSnapshotEntity(String cityCode, LoadTagoArrivalPort.TagoBusArrival arrival) {
        this.id = UUID.randomUUID().toString();
        this.cityCode = cityCode;
        this.sourceNodeId = arrival.sourceNodeId();
        this.nodeName = arrival.nodeName();
        this.sourceRouteId = arrival.sourceRouteId();
        this.routeNo = arrival.routeNo();
        this.routeType = arrival.routeType();
        this.arrivalRemainingStationCount = arrival.arrivalRemainingStationCount();
        this.arrivalRemainingMinutes = arrival.arrivalRemainingMinutes();
        this.arrivalExpectedAt = arrival.arrivalExpectedAt();
        this.vehicleType = arrival.vehicleType();
        this.collectedAt = arrival.collectedAt();
        this.createdAt = Instant.now();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
