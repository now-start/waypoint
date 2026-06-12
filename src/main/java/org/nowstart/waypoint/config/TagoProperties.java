package org.nowstart.waypoint.config;

import org.nowstart.waypoint.application.port.out.TagoCitySettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties(prefix = "waypoint.tago")
public record TagoProperties(
        String baseUrl,
        String serviceKey,
        String cityName,
        String cityCode,
        int numOfRows,
        int maxPages,
        Duration connectTimeout,
        Duration readTimeout
) implements TagoCitySettings {

    public TagoProperties {
        baseUrl = hasText(baseUrl) ? trimTrailingSlash(baseUrl) : "http://apis.data.go.kr/1613000";
        serviceKey = serviceKey == null ? "" : serviceKey.trim();
        cityName = hasText(cityName) ? cityName.trim() : "창원시";
        cityCode = cityCode == null ? "" : cityCode.trim();
        numOfRows = numOfRows > 0 ? numOfRows : 1000;
        maxPages = maxPages > 0 ? maxPages : 100;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
    }

    public boolean hasServiceKey() {
        return hasText(serviceKey);
    }

    public boolean hasCityCode() {
        return hasText(cityCode);
    }

    @Override
    public String targetCityName() {
        return cityName;
    }

    @Override
    public Optional<String> configuredCityCode() {
        return hasCityCode() ? Optional.of(cityCode) : Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
