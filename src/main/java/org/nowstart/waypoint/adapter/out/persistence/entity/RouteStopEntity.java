package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;

@Entity
@IdClass(RouteStopEntity.Id.class)
@Table(
        name = "route_stops",
        indexes = @Index(name = "ix_route_stop_node", columnList = "city_code,source_node_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStopEntity implements Persistable<RouteStopEntity.Id> {

    @jakarta.persistence.Id
    @Column(name = "city_code", nullable = false, length = 20)
    private String cityCode;

    @jakarta.persistence.Id
    @Column(name = "source_route_id", nullable = false, length = 80)
    private String sourceRouteId;

    @Column(name = "source_node_id", nullable = false, length = 80)
    private String sourceNodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
            value = {
                    @JoinColumn(
                            name = "city_code",
                            referencedColumnName = "city_code",
                            insertable = false,
                            updatable = false,
                            nullable = false
                    ),
                    @JoinColumn(
                            name = "source_route_id",
                            referencedColumnName = "source_route_id",
                            insertable = false,
                            updatable = false,
                            nullable = false
                    )
            },
            foreignKey = @ForeignKey(name = "fk_route_stop_route")
    )
    private BusRouteEntity busRoute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
            value = {
                    @JoinColumn(
                            name = "city_code",
                            referencedColumnName = "city_code",
                            insertable = false,
                            updatable = false,
                            nullable = false
                    ),
                    @JoinColumn(
                            name = "source_node_id",
                            referencedColumnName = "source_node_id",
                            insertable = false,
                            updatable = false,
                            nullable = false
                    )
            },
            foreignKey = @ForeignKey(name = "fk_route_stop_stop")
    )
    private BusStopEntity busStop;

    @jakarta.persistence.Id
    @Column(name = "node_order", nullable = false)
    @Getter
    private int nodeOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    public RouteStopEntity(String cityCode, String sourceRouteId, String sourceNodeId, int nodeOrder) {
        this.cityCode = cityCode;
        this.sourceRouteId = sourceRouteId;
        this.sourceNodeId = sourceNodeId;
        this.nodeOrder = nodeOrder;
        this.createdAt = Instant.now();
    }

    public void updateSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    @Override
    public Id getId() {
        return new Id(cityCode, sourceRouteId, nodeOrder);
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
        private int nodeOrder;

        public Id(String cityCode, String sourceRouteId, int nodeOrder) {
            this.cityCode = cityCode;
            this.sourceRouteId = sourceRouteId;
            this.nodeOrder = nodeOrder;
        }
    }
}
