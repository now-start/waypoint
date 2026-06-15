package org.nowstart.waypoint.adapter.in.web;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.config.TagoProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/details")
@RequiredArgsConstructor
public class TransitDetailController {

    private final EntityManager entityManager;
    private final TagoProperties tagoProperties;

    @GetMapping("/{type}")
    public DetailRows getDetails(
            @PathVariable String type,
            @RequestParam(defaultValue = "50") int limit
    ) {
        DetailQuery detailQuery = switch (type) {
            case "routes" -> new DetailQuery(
                    List.of("노선번호", "유형", "기점", "종점", "평일간격", "첫차", "막차", "갱신시각"),
                    """
                            select route_no, route_type, start_node_name, end_node_name,
                                   weekday_interval_minutes, first_vehicle_time, last_vehicle_time, updated_at
                            from bus_routes
                            where city_code = :cityCode
                            order by route_no
                            """
            );
            case "stops" -> new DetailQuery(
                    List.of("정류소번호", "정류소명", "위도", "경도", "최근도착수집", "갱신시각"),
                    """
                            select node_no, node_name, gps_latitude, gps_longitude,
                                   last_arrival_collected_at, updated_at
                            from bus_stops
                            where city_code = :cityCode
                            order by node_name
                            """
            );
            case "route-stops" -> new DetailQuery(
                    List.of("노선번호", "순번", "정류소번호", "정류소명", "기점", "종점"),
                    """
                            select r.route_no, rs.node_order, s.node_no, s.node_name,
                                   r.start_node_name, r.end_node_name
                            from route_stops rs
                            left join bus_routes r
                              on r.city_code = rs.city_code
                             and r.source_route_id = rs.source_route_id
                            left join bus_stops s
                              on s.city_code = rs.city_code
                             and s.source_node_id = rs.source_node_id
                            where rs.city_code = :cityCode
                            order by r.route_no, rs.node_order
                            """
            );
            case "locations" -> new DetailQuery(
                    List.of("수집시각", "노선번호", "차량번호", "정류소ID", "순번", "위도", "경도"),
                    """
                            select collected_at, route_no, vehicle_no, source_node_id,
                                   node_order, gps_latitude, gps_longitude
                            from bus_location_snapshots
                            where city_code = :cityCode
                            order by collected_at desc
                            """
            );
            case "arrivals" -> new DetailQuery(
                    List.of("수집시각", "노선번호", "정류소명", "남은정류소", "남은분", "예상도착", "차량유형"),
                    """
                            select collected_at, route_no, node_name,
                                   arrival_remaining_station_count, arrival_remaining_minutes,
                                   arrival_expected_at, vehicle_type
                            from bus_arrival_snapshots
                            where city_code = :cityCode
                            order by collected_at desc
                            """
            );
            default -> throw new IllegalArgumentException("Unsupported detail type: " + type);
        };

        Query query = entityManager.createNativeQuery(detailQuery.sql());
        query.setParameter("cityCode", tagoProperties.cityCode());
        query.setMaxResults(Math.max(1, Math.min(limit, 200)));

        List<?> rawRows = query.getResultList();
        List<Map<String, Object>> rows = rawRows.stream()
                .map(row -> toMap(detailQuery.columns(), row))
                .toList();

        return new DetailRows(detailQuery.columns(), rows);
    }

    private static Map<String, Object> toMap(List<String> columns, Object row) {
        Object[] values = row instanceof Object[] array ? array : new Object[]{row};
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            result.put(columns.get(index), index < values.length ? values[index] : null);
        }
        return result;
    }

    private record DetailQuery(List<String> columns, String sql) {
    }

    public record DetailRows(List<String> columns, List<Map<String, Object>> rows) {
    }
}
