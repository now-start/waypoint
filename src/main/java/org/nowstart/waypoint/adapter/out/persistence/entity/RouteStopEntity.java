package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "route_stops",
        uniqueConstraints = @UniqueConstraint(name = "uk_route_stop_order", columnNames = {"bus_route_id", "node_order"})
)
public class RouteStopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_route_id", nullable = false)
    private Long busRouteId;

    // 수집/갱신은 ID 컬럼으로 처리하고, 연관 객체는 조회 전용으로만 사용한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bus_route_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_route_stop_route")
    )
    private BusRouteEntity busRoute;

    @Column(name = "bus_stop_id", nullable = false)
    private Long busStopId;

    // 수집/갱신은 ID 컬럼으로 처리하고, 연관 객체는 조회 전용으로만 사용한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bus_stop_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_route_stop_stop")
    )
    private BusStopEntity busStop;

    @Column(name = "node_order", nullable = false)
    private int nodeOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RouteStopEntity() {
    }

    public RouteStopEntity(Long busRouteId, Long busStopId, int nodeOrder) {
        this.busRouteId = busRouteId;
        this.busStopId = busStopId;
        this.nodeOrder = nodeOrder;
        this.createdAt = Instant.now();
    }

    public void updateBusStopId(Long busStopId) {
        this.busStopId = busStopId;
    }
}
