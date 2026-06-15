package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.out.TagoCitySettings;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TagoCityCodeResolver {

    private final TagoCitySettings settings;

    String resolve() {
        return settings.cityCode();
    }
}
