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
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;

import java.time.Instant;

@Entity
@Table(
        name = "bus_location_snapshots",
        indexes = {
                @Index(name = "ix_location_route_collected", columnList = "bus_route_id,collected_at"),
                @Index(name = "ix_location_vehicle_collected", columnList = "vehicle_no,collected_at")
        }
)
public class BusLocationSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_route_id")
    private Long busRouteId;

    // 기준 데이터 매칭 실패 시에도 스냅샷은 보존하므로 nullable 조회 전용 연관관계로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bus_route_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_location_snapshot_route")
    )
    private BusRouteEntity busRoute;

    @Column(name = "source_route_id", nullable = false, length = 80)
    private String sourceRouteId;

    @Column(name = "route_no", length = 80)
    private String routeNo;

    @Column(name = "vehicle_no", length = 80)
    private String vehicleNo;

    @Column(name = "source_node_id", length = 80)
    private String sourceNodeId;

    @Column(name = "node_order")
    private Integer nodeOrder;

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BusLocationSnapshotEntity() {
    }

    public BusLocationSnapshotEntity(Long busRouteId, LoadTagoLocationPort.TagoBusLocation location) {
        this.busRouteId = busRouteId;
        this.sourceRouteId = location.sourceRouteId();
        this.routeNo = location.routeNo();
        this.vehicleNo = location.vehicleNo();
        this.sourceNodeId = location.sourceNodeId();
        this.nodeOrder = location.nodeOrder();
        this.gpsLatitude = location.gpsLatitude();
        this.gpsLongitude = location.gpsLongitude();
        this.collectedAt = location.collectedAt();
        this.createdAt = Instant.now();
    }
}
