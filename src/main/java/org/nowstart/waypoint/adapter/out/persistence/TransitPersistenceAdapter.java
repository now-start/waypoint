package org.nowstart.waypoint.adapter.out.persistence;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusArrivalSnapshotEntity;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusLocationSnapshotEntity;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusRouteEntity;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusStopEntity;
import org.nowstart.waypoint.adapter.out.persistence.entity.CollectionRunEntity;
import org.nowstart.waypoint.adapter.out.persistence.entity.RouteStopEntity;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusArrivalSnapshotJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusLocationSnapshotJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusRouteJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusStopJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.CollectionRunJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.RouteStopJpaRepository;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TransitPersistenceAdapter implements SaveTransitDataPort, LoadTransitDataPort {

    private final BusRouteJpaRepository busRouteRepository;
    private final BusStopJpaRepository busStopRepository;
    private final RouteStopJpaRepository routeStopRepository;
    private final BusLocationSnapshotJpaRepository locationSnapshotRepository;
    private final BusArrivalSnapshotJpaRepository arrivalSnapshotRepository;
    private final CollectionRunJpaRepository collectionRunRepository;

    public TransitPersistenceAdapter(
            BusRouteJpaRepository busRouteRepository,
            BusStopJpaRepository busStopRepository,
            RouteStopJpaRepository routeStopRepository,
            BusLocationSnapshotJpaRepository locationSnapshotRepository,
            BusArrivalSnapshotJpaRepository arrivalSnapshotRepository,
            CollectionRunJpaRepository collectionRunRepository
    ) {
        this.busRouteRepository = busRouteRepository;
        this.busStopRepository = busStopRepository;
        this.routeStopRepository = routeStopRepository;
        this.locationSnapshotRepository = locationSnapshotRepository;
        this.arrivalSnapshotRepository = arrivalSnapshotRepository;
        this.collectionRunRepository = collectionRunRepository;
    }

    @Override
    @Transactional
    public Long startCollectionRun(CollectionApiType apiType, String requestKey, String requestParamsJson) {
        return collectionRunRepository.save(new CollectionRunEntity(apiType, requestKey, requestParamsJson)).getId();
    }

    @Override
    @Transactional
    public void finishCollectionRun(
            Long runId,
            CollectionStatus status,
            int httpStatus,
            String resultCode,
            String resultMessage,
            int rowCount,
            String errorMessage
    ) {
        CollectionRunEntity run = collectionRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("수집 실행 이력을 찾을 수 없습니다. id=" + runId));
        run.finish(status, httpStatus, resultCode, resultMessage, rowCount, errorMessage);
    }

    @Override
    @Transactional
    public int saveRoutes(String cityCode, List<LoadTagoRoutePort.TagoRoute> routes) {
        int saved = 0;
        for (LoadTagoRoutePort.TagoRoute route : routes) {
            if (route.sourceRouteId() == null) {
                continue;
            }
            BusRouteEntity entity = busRouteRepository.findByCityCodeAndSourceRouteId(cityCode, route.sourceRouteId())
                    .orElseGet(() -> BusRouteEntity.create(cityCode, route));
            entity.update(route);
            busRouteRepository.save(entity);
            saved++;
        }
        return saved;
    }

    @Override
    @Transactional
    public int saveRouteStops(String cityCode, String sourceRouteId, List<LoadTagoRoutePort.TagoRouteStop> stops) {
        Optional<BusRouteEntity> route = busRouteRepository.findByCityCodeAndSourceRouteId(cityCode, sourceRouteId);
        if (route.isEmpty()) {
            return 0;
        }

        int saved = 0;
        for (LoadTagoRoutePort.TagoRouteStop stop : stops) {
            if (stop.sourceNodeId() == null || stop.nodeOrder() == null) {
                continue;
            }
            BusStopEntity stopEntity = busStopRepository.findByCityCodeAndSourceNodeId(cityCode, stop.sourceNodeId())
                    .orElseGet(() -> BusStopEntity.create(cityCode, stop));
            stopEntity.update(stop);
            BusStopEntity savedStop = busStopRepository.save(stopEntity);

            RouteStopEntity routeStop = routeStopRepository.findByBusRouteIdAndNodeOrder(route.get().getId(), stop.nodeOrder())
                    .orElseGet(() -> new RouteStopEntity(route.get().getId(), savedStop.getId(), stop.nodeOrder()));
            routeStop.updateBusStopId(savedStop.getId());
            routeStopRepository.save(routeStop);
            saved++;
        }
        return saved;
    }

    @Override
    @Transactional
    public int saveLocationSnapshots(
            String cityCode,
            String sourceRouteId,
            List<LoadTagoLocationPort.TagoBusLocation> locations
    ) {
        Long busRouteId = busRouteRepository.findByCityCodeAndSourceRouteId(cityCode, sourceRouteId)
                .map(BusRouteEntity::getId)
                .orElse(null);
        List<BusLocationSnapshotEntity> entities = locations.stream()
                .filter(location -> location.sourceRouteId() != null)
                .map(location -> new BusLocationSnapshotEntity(busRouteId, location))
                .toList();
        locationSnapshotRepository.saveAll(entities);
        return entities.size();
    }

    @Override
    @Transactional
    public int saveArrivalSnapshots(
            String cityCode,
            String sourceNodeId,
            List<LoadTagoArrivalPort.TagoBusArrival> arrivals
    ) {
        BusStopEntity stop = busStopRepository.findByCityCodeAndSourceNodeId(cityCode, sourceNodeId)
                .orElse(null);
        Long busStopId = stop == null ? null : stop.getId();

        List<String> sourceRouteIds = arrivals.stream()
                .map(LoadTagoArrivalPort.TagoBusArrival::sourceRouteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Long> routeIdMap = sourceRouteIds.isEmpty()
                ? Map.of()
                : busRouteRepository.findAllByCityCodeAndSourceRouteIdIn(cityCode, sourceRouteIds).stream()
                .collect(Collectors.toMap(
                        BusRouteEntity::getSourceRouteId,
                        BusRouteEntity::getId,
                        (left, right) -> left
                ));

        List<BusArrivalSnapshotEntity> entities = arrivals.stream()
                .map(arrival -> new BusArrivalSnapshotEntity(
                        busStopId,
                        routeIdMap.get(arrival.sourceRouteId()),
                        arrival
                ))
                .toList();
        arrivalSnapshotRepository.saveAll(entities);

        arrivals.stream()
                .map(LoadTagoArrivalPort.TagoBusArrival::collectedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(collectedAt -> {
                    if (stop != null) {
                        stop.markArrivalCollected(collectedAt);
                    }
                });
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteReference> loadRoutes(String cityCode) {
        return busRouteRepository.findAllByCityCodeOrderByRouteNoAsc(cityCode).stream()
                .map(route -> new RouteReference(route.getId(), route.getCityCode(), route.getSourceRouteId(), route.getRouteNo()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopReference> loadStopsBySourceNodeIds(String cityCode, List<String> sourceNodeIds) {
        return busStopRepository.findAllByCityCodeAndSourceNodeIdIn(cityCode, sourceNodeIds).stream()
                .map(stop -> new StopReference(
                        stop.getId(),
                        stop.getCityCode(),
                        stop.getSourceNodeId(),
                        stop.getNodeName(),
                        stop.getLastArrivalCollectedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> latestLocationCollectedAt() {
        return locationSnapshotRepository.findLatestCollectedAt();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> latestArrivalCollectedAt() {
        return arrivalSnapshotRepository.findLatestCollectedAt();
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionStatusSnapshot loadCollectionStatus(int recentRunLimit) {
        return new CollectionStatusSnapshot(
                busRouteRepository.count(),
                busStopRepository.count(),
                routeStopRepository.count(),
                locationSnapshotRepository.count(),
                arrivalSnapshotRepository.count(),
                latestLocationCollectedAt().orElse(null),
                latestArrivalCollectedAt().orElse(null),
                loadRecentRuns(recentRunLimit)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionRunSnapshot> loadRecentRuns(int limit) {
        return collectionRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(this::toRunView)
                .toList();
    }

    private CollectionRunSnapshot toRunView(CollectionRunEntity run) {
        return new CollectionRunSnapshot(
                run.getId(),
                run.getApiType(),
                run.getStatus(),
                run.getRequestKey(),
                run.getRowCount(),
                run.getResultCode(),
                run.getResultMessage(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt()
        );
    }
}
