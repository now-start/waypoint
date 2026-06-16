package org.nowstart.waypoint.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TransitPersistenceAdapter implements SaveTransitDataPort, LoadTransitDataPort {

    private final BusRouteJpaRepository busRouteRepository;
    private final BusStopJpaRepository busStopRepository;
    private final RouteStopJpaRepository routeStopRepository;
    private final BusLocationSnapshotJpaRepository locationSnapshotRepository;
    private final BusArrivalSnapshotJpaRepository arrivalSnapshotRepository;
    private final CollectionRunJpaRepository collectionRunRepository;

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
        List<LoadTagoRoutePort.TagoRoute> validRoutes = routes.stream()
                .filter(route -> route.sourceRouteId() != null)
                .toList();
        if (validRoutes.isEmpty()) {
            return 0;
        }

        List<LoadTagoRoutePort.TagoRoute> uniqueRoutes = distinctByKey(
                validRoutes,
                LoadTagoRoutePort.TagoRoute::sourceRouteId
        );
        List<String> sourceRouteIds = uniqueRoutes.stream()
                .map(LoadTagoRoutePort.TagoRoute::sourceRouteId)
                .toList();
        Map<String, BusRouteEntity> routeEntityMap = busRouteRepository
                .findAllByCityCodeAndSourceRouteIdIn(cityCode, sourceRouteIds).stream()
                .collect(Collectors.toMap(BusRouteEntity::getSourceRouteId, Function.identity(), (left, right) -> left));

        List<LoadTagoRoutePort.TagoRoute> insertRoutes = uniqueRoutes.stream()
                .filter(route -> !routeEntityMap.containsKey(route.sourceRouteId()))
                .toList();
        List<LoadTagoRoutePort.TagoRoute> updateRoutes = uniqueRoutes.stream()
                .filter(route -> routeEntityMap.containsKey(route.sourceRouteId()))
                .toList();

        List<BusRouteEntity> entities = new java.util.ArrayList<>(uniqueRoutes.size());
        insertRoutes.stream()
                .map(route -> BusRouteEntity.create(cityCode, route))
                .forEach(entities::add);
        updateRoutes.stream()
                .map(route -> {
                    BusRouteEntity entity = routeEntityMap.get(route.sourceRouteId());
                    entity.update(route);
                    return entity;
                })
                .forEach(entities::add);
        busRouteRepository.saveAll(entities);
        return uniqueRoutes.size();
    }

    @Override
    @Transactional
    public int saveRouteStops(String cityCode, String sourceRouteId, List<LoadTagoRoutePort.TagoRouteStop> stops) {
        if (!busRouteRepository.existsByCityCodeAndSourceRouteId(cityCode, sourceRouteId)) {
            return 0;
        }

        List<LoadTagoRoutePort.TagoRouteStop> validStops = stops.stream()
                .filter(stop -> stop.sourceNodeId() != null && stop.nodeOrder() != null)
                .toList();
        if (validStops.isEmpty()) {
            return 0;
        }

        List<LoadTagoRoutePort.TagoRouteStop> uniqueStops = distinctByKey(
                validStops,
                LoadTagoRoutePort.TagoRouteStop::sourceNodeId
        );
        List<String> sourceNodeIds = uniqueStops.stream()
                .map(LoadTagoRoutePort.TagoRouteStop::sourceNodeId)
                .toList();
        Map<String, BusStopEntity> stopEntityMap = busStopRepository
                .findAllByCityCodeAndSourceNodeIdIn(cityCode, sourceNodeIds).stream()
                .collect(Collectors.toMap(BusStopEntity::getSourceNodeId, Function.identity(), (left, right) -> left));

        List<LoadTagoRoutePort.TagoRouteStop> insertStops = uniqueStops.stream()
                .filter(stop -> !stopEntityMap.containsKey(stop.sourceNodeId()))
                .toList();
        List<LoadTagoRoutePort.TagoRouteStop> updateStops = uniqueStops.stream()
                .filter(stop -> stopEntityMap.containsKey(stop.sourceNodeId()))
                .toList();

        List<BusStopEntity> stopEntities = new java.util.ArrayList<>(uniqueStops.size());
        insertStops.stream()
                .map(stop -> BusStopEntity.create(cityCode, stop))
                .forEach(stopEntities::add);
        updateStops.stream()
                .map(stop -> {
                    BusStopEntity entity = stopEntityMap.get(stop.sourceNodeId());
                    entity.update(stop);
                    return entity;
                })
                .forEach(stopEntities::add);

        List<BusStopEntity> savedStops = busStopRepository.saveAll(stopEntities);
        Map<String, BusStopEntity> savedStopMap = savedStops.stream()
                .collect(Collectors.toMap(BusStopEntity::getSourceNodeId, Function.identity(), (left, right) -> left));

        List<LoadTagoRoutePort.TagoRouteStop> uniqueRouteStops = distinctByKey(
                validStops,
                LoadTagoRoutePort.TagoRouteStop::nodeOrder
        );
        List<Integer> nodeOrders = uniqueRouteStops.stream()
                .map(LoadTagoRoutePort.TagoRouteStop::nodeOrder)
                .toList();
        Map<Integer, RouteStopEntity> routeStopMap = routeStopRepository
                .findAllByCityCodeAndSourceRouteIdAndNodeOrderIn(cityCode, sourceRouteId, nodeOrders).stream()
                .collect(Collectors.toMap(RouteStopEntity::getNodeOrder, Function.identity(), (left, right) -> left));

        List<LoadTagoRoutePort.TagoRouteStop> insertRouteStops = uniqueRouteStops.stream()
                .filter(stop -> !routeStopMap.containsKey(stop.nodeOrder()))
                .toList();
        List<LoadTagoRoutePort.TagoRouteStop> updateRouteStops = uniqueRouteStops.stream()
                .filter(stop -> routeStopMap.containsKey(stop.nodeOrder()))
                .toList();

        List<RouteStopEntity> routeStopEntities = new java.util.ArrayList<>(uniqueRouteStops.size());
        insertRouteStops.stream()
                .map(stop -> new RouteStopEntity(
                        cityCode,
                        sourceRouteId,
                        savedSourceNodeId(savedStopMap, stop),
                        stop.nodeOrder()
                ))
                .forEach(routeStopEntities::add);
        updateRouteStops.stream()
                .map(stop -> {
                    RouteStopEntity entity = routeStopMap.get(stop.nodeOrder());
                    entity.updateSourceNodeId(savedSourceNodeId(savedStopMap, stop));
                    return entity;
                })
                .forEach(routeStopEntities::add);
        routeStopRepository.saveAll(routeStopEntities);
        return uniqueRouteStops.size();
    }

    @Override
    @Transactional
    public int saveLocationSnapshots(String cityCode, List<LoadTagoLocationPort.TagoBusLocation> locations) {
        List<BusLocationSnapshotEntity> entities = locations.stream()
                .filter(location -> location.sourceRouteId() != null)
                .map(location -> new BusLocationSnapshotEntity(cityCode, location))
                .toList();
        locationSnapshotRepository.saveAll(entities);
        return entities.size();
    }

    @Override
    @Transactional
    public int saveArrivalSnapshots(
            String cityCode,
            List<LoadTagoArrivalPort.TagoBusArrival> arrivals,
            Map<String, Instant> collectedAtByStop
    ) {
        List<BusArrivalSnapshotEntity> entities = arrivals.stream()
                .filter(arrival -> arrival.sourceNodeId() != null)
                .map(arrival -> new BusArrivalSnapshotEntity(cityCode, arrival))
                .toList();
        arrivalSnapshotRepository.saveAll(entities);

        Map<String, Instant> latestCollectedAtByStop = new LinkedHashMap<>(collectedAtByStop);
        arrivals.stream()
                .filter(arrival -> arrival.sourceNodeId() != null)
                .filter(arrival -> arrival.collectedAt() != null)
                .forEach(arrival -> latestCollectedAtByStop.merge(
                        arrival.sourceNodeId(),
                        arrival.collectedAt(),
                        (left, right) -> left.isAfter(right) ? left : right
                ));
        if (!latestCollectedAtByStop.isEmpty()) {
            busStopRepository.findAllByCityCodeAndSourceNodeIdIn(cityCode, latestCollectedAtByStop.keySet())
                    .forEach(stop -> stop.markArrivalCollected(latestCollectedAtByStop.get(stop.getSourceNodeId())));
        }
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteReference> loadRoutes(String cityCode) {
        List<BusRouteEntity> routes = busRouteRepository.findAllByCityCodeOrderByRouteNoAsc(cityCode);
        List<String> sourceRouteIds = routes.stream()
                .map(BusRouteEntity::getSourceRouteId)
                .toList();
        Map<String, Instant> latestLocationCollectedAtByRoute = sourceRouteIds.isEmpty()
                ? Map.of()
                : locationSnapshotRepository.findLatestCollectedAtByCityCodeAndSourceRouteIdIn(
                                cityCode,
                                sourceRouteIds
                        ).stream()
                        .collect(Collectors.toMap(
                                BusLocationSnapshotJpaRepository.RouteLatestCollectedAt::getSourceRouteId,
                                BusLocationSnapshotJpaRepository.RouteLatestCollectedAt::getCollectedAt,
                                (left, right) -> left.isAfter(right) ? left : right
                        ));
        return routes.stream()
                .map(route -> new RouteReference(
                        route.getCityCode(),
                        route.getSourceRouteId(),
                        route.getRouteNo(),
                        route.getWeekdayIntervalMinutes(),
                        route.getSaturdayIntervalMinutes(),
                        route.getSundayIntervalMinutes(),
                        route.getFirstVehicleTime(),
                        route.getLastVehicleTime(),
                        latestLocationCollectedAtByRoute.get(route.getSourceRouteId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopReference> loadStops(String cityCode) {
        return busStopRepository.findAllByCityCodeOrderBySourceNodeIdAsc(cityCode).stream()
                .map(stop -> new StopReference(
                        stop.getCityCode(),
                        stop.getSourceNodeId(),
                        stop.getNodeName(),
                        stop.getLastArrivalCollectedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutePathStopReference> loadRoutePathStops(String cityCode, int limit) {
        return routeStopRepository.findMapPathStopsByCityCode(
                        cityCode,
                        PageRequest.of(0, Math.max(1, limit))
                ).stream()
                .map(stop -> new RoutePathStopReference(
                        stop.getSourceRouteId(),
                        stop.getRouteNo(),
                        stop.getRouteType(),
                        stop.getStartNodeName(),
                        stop.getEndNodeName(),
                        stop.getSourceNodeId(),
                        stop.getNodeName(),
                        stop.getNodeOrder(),
                        stop.getGpsLatitude(),
                        stop.getGpsLongitude(),
                        stop.getLastArrivalCollectedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationSnapshotReference> loadRecentLocationSnapshots(String cityCode, Instant since, int limit) {
        return locationSnapshotRepository.findRecentByCityCode(cityCode, since, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(snapshot -> new LocationSnapshotReference(
                        snapshot.getSourceRouteId(),
                        snapshot.getRouteNo(),
                        snapshot.getVehicleNo(),
                        snapshot.getSourceNodeId(),
                        snapshot.getNodeOrder(),
                        snapshot.getGpsLatitude(),
                        snapshot.getGpsLongitude(),
                        snapshot.getCollectedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArrivalSnapshotReference> loadRecentArrivalSnapshots(String cityCode, Instant since, int limit) {
        return arrivalSnapshotRepository.findRecentByCityCode(cityCode, since, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(snapshot -> new ArrivalSnapshotReference(
                        snapshot.getSourceRouteId(),
                        snapshot.getRouteNo(),
                        snapshot.getSourceNodeId(),
                        snapshot.getNodeName(),
                        snapshot.getArrivalRemainingMinutes(),
                        snapshot.getArrivalExpectedAt(),
                        snapshot.getCollectedAt()
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

    private static String savedSourceNodeId(
            Map<String, BusStopEntity> savedStopMap,
            LoadTagoRoutePort.TagoRouteStop stop
    ) {
        BusStopEntity savedStop = savedStopMap.get(stop.sourceNodeId());
        if (savedStop == null) {
            throw new IllegalStateException("저장된 정류장을 찾을 수 없습니다. sourceNodeId=" + stop.sourceNodeId());
        }
        return savedStop.getSourceNodeId();
    }

    private static <T, K> List<T> distinctByKey(List<T> values, Function<T, K> keyExtractor) {
        Map<K, T> map = new LinkedHashMap<>();
        for (T value : values) {
            map.put(keyExtractor.apply(value), value);
        }
        return List.copyOf(map.values());
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
