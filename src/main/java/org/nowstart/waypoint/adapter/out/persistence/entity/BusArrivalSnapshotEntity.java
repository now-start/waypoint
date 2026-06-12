package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;

import java.time.Instant;

@Entity
@Table(
        name = "bus_arrival_snapshots",
        indexes = {
                @Index(name = "ix_arrival_stop_collected", columnList = "bus_stop_id,collected_at"),
                @Index(name = "ix_arrival_route_collected", columnList = "bus_route_id,collected_at")
        }
)
public class BusArrivalSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_stop_id")
    private Long busStopId;

    // 기준 정류소 매칭 실패 시에도 원본 도착 스냅샷은 보존한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bus_stop_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_arrival_snapshot_stop")
    )
    private BusStopEntity busStop;

    @Column(name = "source_node_id", nullable = false, length = 80)
    private String sourceNodeId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "bus_route_id")
    private Long busRouteId;

    // 기준 노선 매칭 실패 시에도 원본 도착 스냅샷은 보존한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bus_route_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_arrival_snapshot_route")
    )
    private BusRouteEntity busRoute;

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

    protected BusArrivalSnapshotEntity() {
    }

    public BusArrivalSnapshotEntity(
            Long busStopId,
            Long busRouteId,
            LoadTagoArrivalPort.TagoBusArrival arrival
    ) {
        this.busStopId = busStopId;
        this.sourceNodeId = arrival.sourceNodeId();
        this.nodeName = arrival.nodeName();
        this.busRouteId = busRouteId;
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
}
