package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;

import java.time.Instant;

@Entity
@Table(
        name = "bus_stops",
        uniqueConstraints = @UniqueConstraint(name = "uk_bus_stop_source", columnNames = {"city_code", "source_node_id"})
)
public class BusStopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_code", nullable = false, length = 20)
    private String cityCode;

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

    protected BusStopEntity() {
    }

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

    public Long getId() {
        return id;
    }

    public String getCityCode() {
        return cityCode;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Instant getLastArrivalCollectedAt() {
        return lastArrivalCollectedAt;
    }
}
