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
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "bus_location_snapshots",
        indexes = {
                @Index(name = "ix_location_route_collected", columnList = "city_code,source_route_id,collected_at"),
                @Index(name = "ix_location_vehicle_collected", columnList = "vehicle_no,collected_at"),
                @Index(name = "ix_location_city_collected_id", columnList = "city_code,collected_at,id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusLocationSnapshotEntity implements Persistable<String> {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "city_code", nullable = false, length = 20)
    private String cityCode;

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

    @Transient
    private boolean newEntity = true;

    public BusLocationSnapshotEntity(String cityCode, LoadTagoLocationPort.TagoBusLocation location) {
        this.id = UUID.randomUUID().toString();
        this.cityCode = cityCode;
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
