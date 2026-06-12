package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.TagoCitySettings;
import org.springframework.stereotype.Component;

@Component
class TagoCityCodeResolver {

    private final TagoCitySettings settings;
    private final LoadTagoRoutePort routePort;

    TagoCityCodeResolver(TagoCitySettings settings, LoadTagoRoutePort routePort) {
        this.settings = settings;
        this.routePort = routePort;
    }

    String resolve() {
        return settings.configuredCityCode()
                .orElseGet(this::loadCityCode);
    }

    private String loadCityCode() {
        return routePort.loadCities().stream()
                .filter(city -> city.cityName() != null && city.cityName().contains(settings.targetCityName()))
                .findFirst()
                .map(LoadTagoRoutePort.TagoCity::cityCode)
                .orElseThrow(() -> new IllegalStateException(
                        "TAGO 도시코드 목록에서 " + settings.targetCityName() + " 도시코드를 찾지 못했습니다."
                ));
    }
}
