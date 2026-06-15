package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;

@Entity
@IdClass(BusStopEntity.Id.class)
@Table(name = "bus_stops")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusStopEntity implements Persistable<BusStopEntity.Id> {

    @jakarta.persistence.Id
    @Column(name = "city_code", nullable = false, length = 20)
    private String cityCode;

    @jakarta.persistence.Id
    @Column(name = "source_node_id", nullable = false, length = 80)
    private String sourceNodeId;

    @Column(name = "node_no", length = 80)
    private String nodeNo;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "last_arrival_collected_at")
    private Instant lastArrivalCollectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    private BusStopEntity(String cityCode, String sourceNodeId) {
        this.cityCode = cityCode;
        this.sourceNodeId = sourceNodeId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BusStopEntity create(String cityCode, LoadTagoRoutePort.TagoRouteStop stop) {
        BusStopEntity entity = new BusStopEntity(cityCode, stop.sourceNodeId());
        entity.update(stop);
        return entity;
    }

    public void update(LoadTagoRoutePort.TagoRouteStop stop) {
        this.nodeNo = stop.nodeNo();
        this.nodeName = stop.nodeName();
        this.gpsLatitude = stop.gpsLatitude();
        this.gpsLongitude = stop.gpsLongitude();
        this.updatedAt = Instant.now();
    }

    public void markArrivalCollected(Instant collectedAt) {
        this.lastArrivalCollectedAt = collectedAt;
        this.updatedAt = Instant.now();
    }

    @Override
    public Id getId() {
        return new Id(cityCode, sourceNodeId);
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

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        private String cityCode;
        private String sourceNodeId;

        public Id(String cityCode, String sourceNodeId) {
            this.cityCode = cityCode;
            this.sourceNodeId = sourceNodeId;
        }
    }
}
