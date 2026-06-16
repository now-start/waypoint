package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.QueryTransitMapUseCase;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransitMapInteractor implements QueryTransitMapUseCase {

    private static final int MAX_ROUTE_PATH_ROWS = 100_000;
    private static final int MAX_LOCATION_SNAPSHOTS = 10_000;
    private static final Duration LOCATION_LOOKBACK = Duration.ofMinutes(45);
    private static final Duration LOCATION_NORMAL_THRESHOLD = Duration.ofMinutes(2);
    private static final Duration LOCATION_DELAYED_THRESHOLD = Duration.ofMinutes(5);
    private static final MapBounds CHANGWON_BOUNDS = new MapBounds(34.88, 35.45, 128.32, 129.02);

    private final LoadTransitDataPort loadTransitDataPort;
    private final TagoCityCodeResolver cityCodeResolver;

    @Override
    public TransitMapView getMap() {
        String cityCode = cityCodeResolver.resolve();
        Instant now = Instant.now();
        List<MapVehicle> vehicles = latestVehicles(cityCode, now);
        Set<String> activeRouteIds = vehicles.stream()
                .map(MapVehicle::sourceRouteId)
                .collect(Collectors.toSet());
        List<MapRoute> routes = mapRoutes(cityCode, activeRouteIds);

        int stopCount = routes.stream()
                .mapToInt(route -> route.stops().size())
                .sum();
        int delayedVehicleCount = (int) vehicles.stream()
                .filter(vehicle -> !"normal".equals(vehicle.freshness()))
                .count();

        return new TransitMapView(
                now,
                CHANGWON_BOUNDS,
                new MapSummary(routes.size(), stopCount, vehicles.size(), delayedVehicleCount),
                routes,
                vehicles
        );
    }

    private List<MapRoute> mapRoutes(String cityCode, Set<String> activeRouteIds) {
        Map<String, List<LoadTransitDataPort.RoutePathStopReference>> stopsByRoute = loadTransitDataPort
                .loadRoutePathStops(cityCode, MAX_ROUTE_PATH_ROWS).stream()
                .filter(stop -> stop.sourceRouteId() != null)
                .collect(Collectors.groupingBy(
                        LoadTransitDataPort.RoutePathStopReference::sourceRouteId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return stopsByRoute.values().stream()
                .sorted(Comparator
                        .comparing((List<LoadTransitDataPort.RoutePathStopReference> stops) ->
                                !activeRouteIds.contains(stops.getFirst().sourceRouteId()))
                        .thenComparing(stops -> routeNo(stops.getFirst())))
                .map(this::toMapRoute)
                .toList();
    }

    private MapRoute toMapRoute(List<LoadTransitDataPort.RoutePathStopReference> stops) {
        LoadTransitDataPort.RoutePathStopReference first = stops.getFirst();
        return new MapRoute(
                first.sourceRouteId(),
                first.routeNo(),
                first.routeType(),
                first.startNodeName(),
                first.endNodeName(),
                stops.stream()
                        .sorted(Comparator.comparing(
                                LoadTransitDataPort.RoutePathStopReference::nodeOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .map(stop -> new MapStop(
                                stop.sourceNodeId(),
                                stop.nodeName(),
                                stop.nodeOrder(),
                                stop.gpsLatitude(),
                                stop.gpsLongitude(),
                                stop.lastArrivalCollectedAt()
                        ))
                        .toList()
        );
    }

    private List<MapVehicle> latestVehicles(String cityCode, Instant now) {
        Map<String, LoadTransitDataPort.LocationSnapshotReference> latestByVehicle = loadTransitDataPort
                .loadRecentLocationSnapshots(cityCode, now.minus(LOCATION_LOOKBACK), MAX_LOCATION_SNAPSHOTS).stream()
                .filter(snapshot -> snapshot.sourceRouteId() != null)
                .filter(snapshot -> snapshot.vehicleNo() != null)
                .filter(snapshot -> snapshot.gpsLatitude() != null && snapshot.gpsLongitude() != null)
                .collect(Collectors.toMap(
                        snapshot -> snapshot.sourceRouteId() + "|" + snapshot.vehicleNo(),
                        Function.identity(),
                        (left, right) -> left.collectedAt().isAfter(right.collectedAt()) ? left : right,
                        LinkedHashMap::new
                ));

        return latestByVehicle.values().stream()
                .sorted(Comparator
                        .comparing(LoadTransitDataPort.LocationSnapshotReference::collectedAt).reversed()
                        .thenComparing(LoadTransitDataPort.LocationSnapshotReference::routeNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LoadTransitDataPort.LocationSnapshotReference::vehicleNo, Comparator.nullsLast(String::compareTo)))
                .map(snapshot -> toMapVehicle(snapshot, now))
                .toList();
    }

    private MapVehicle toMapVehicle(LoadTransitDataPort.LocationSnapshotReference snapshot, Instant now) {
        return new MapVehicle(
                snapshot.sourceRouteId(),
                snapshot.routeNo(),
                snapshot.vehicleNo(),
                snapshot.sourceNodeId(),
                snapshot.nodeOrder(),
                snapshot.gpsLatitude(),
                snapshot.gpsLongitude(),
                snapshot.collectedAt(),
                freshness(snapshot.collectedAt(), now)
        );
    }

    private static String freshness(Instant collectedAt, Instant now) {
        Duration age = Duration.between(collectedAt, now);
        if (age.compareTo(LOCATION_NORMAL_THRESHOLD) <= 0) {
            return "normal";
        }
        if (age.compareTo(LOCATION_DELAYED_THRESHOLD) <= 0) {
            return "delayed";
        }
        return "stale";
    }

    private static String routeNo(LoadTransitDataPort.RoutePathStopReference stop) {
        return stop.routeNo() == null ? "" : stop.routeNo();
    }
}
