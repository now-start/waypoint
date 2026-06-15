package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.out.TagoApiException;

final class CollectionFailureMessages {

    private static final int MAX_MESSAGE_LENGTH = 300;

    private CollectionFailureMessages() {
    }

    static String describe(RuntimeException ex) {
        if (ex instanceof TagoApiException tagoException) {
            return "httpStatus=" + tagoException.getHttpStatus()
                    + ", resultCode=" + sanitize(tagoException.getResultCode())
                    + ", resultMessage=" + sanitize(tagoException.getResultMessage());
        }
        return sanitize(ex.getMessage());
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)(serviceKey=)[^&\\s]+", "$1***")
                .replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.length() > MAX_MESSAGE_LENGTH ? sanitized.substring(0, MAX_MESSAGE_LENGTH) : sanitized;
    }
}
