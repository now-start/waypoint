package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "bus_stop_id", nullable = false)
    private Long busStopId;

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
