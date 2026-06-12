package org.nowstart.waypoint.application.port.out;

import java.util.Optional;

public interface TagoCitySettings {

    String targetCityName();

    Optional<String> configuredCityCode();
}
