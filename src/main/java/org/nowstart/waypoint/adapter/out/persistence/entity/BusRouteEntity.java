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
@IdClass(BusRouteEntity.Id.class)
@Table(name = "bus_routes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusRouteEntity implements Persistable<BusRouteEntity.Id> {

    @jakarta.persistence.Id
    @Column(name = "city_code", nullable = false, length = 20)
    private String cityCode;

    @jakarta.persistence.Id
    @Column(name = "source_route_id", nullable = false, length = 80)
    private String sourceRouteId;

    @Column(name = "route_no", length = 80)
    private String routeNo;

    @Column(name = "route_type", length = 80)
    private String routeType;

    @Column(name = "start_node_name")
    private String startNodeName;

    @Column(name = "end_node_name")
    private String endNodeName;

    @Column(name = "weekday_interval_minutes")
    private Integer weekdayIntervalMinutes;

    @Column(name = "saturday_interval_minutes")
    private Integer saturdayIntervalMinutes;

    @Column(name = "sunday_interval_minutes")
    private Integer sundayIntervalMinutes;

    @Column(name = "first_vehicle_time", length = 20)
    private String firstVehicleTime;

    @Column(name = "last_vehicle_time", length = 20)
    private String lastVehicleTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    private BusRouteEntity(String cityCode, String sourceRouteId) {
        this.cityCode = cityCode;
        this.sourceRouteId = sourceRouteId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BusRouteEntity create(String cityCode, LoadTagoRoutePort.TagoRoute route) {
        BusRouteEntity entity = new BusRouteEntity(cityCode, route.sourceRouteId());
        entity.update(route);
        return entity;
    }

    public void update(LoadTagoRoutePort.TagoRoute route) {
        this.routeNo = route.routeNo();
        this.routeType = route.routeType();
        this.startNodeName = route.startNodeName();
        this.endNodeName = route.endNodeName();
        this.weekdayIntervalMinutes = route.weekdayIntervalMinutes();
        this.saturdayIntervalMinutes = route.saturdayIntervalMinutes();
        this.sundayIntervalMinutes = route.sundayIntervalMinutes();
        this.firstVehicleTime = route.firstVehicleTime();
        this.lastVehicleTime = route.lastVehicleTime();
        this.updatedAt = Instant.now();
    }

    @Override
    public Id getId() {
        return new Id(cityCode, sourceRouteId);
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
        private String sourceRouteId;

        public Id(String cityCode, String sourceRouteId) {
            this.cityCode = cityCode;
            this.sourceRouteId = sourceRouteId;
        }
    }
}
