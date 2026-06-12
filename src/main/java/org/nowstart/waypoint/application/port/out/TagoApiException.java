package org.nowstart.waypoint.application.port.out;

public class TagoApiException extends RuntimeException {

    private final int httpStatus;
    private final String resultCode;
    private final String resultMessage;

    public TagoApiException(String message, int httpStatus, String resultCode, String resultMessage) {
        super(message);
        this.httpStatus = httpStatus;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
    }

    public TagoApiException(String message, int httpStatus, String resultCode, String resultMessage, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }
}
