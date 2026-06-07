package ar.com.personalfinances.webclient;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class RestConnectorException extends Exception {

    private int status = -1;
    private HttpStatusCode statusInfo;
    private Object entityError;

    public RestConnectorException(String message, int status, HttpStatusCode statusInfo, Object entityError) {
        super(message);
        this.status = status;
        this.statusInfo = statusInfo;
        this.entityError = entityError;
    }

    public RestConnectorException(String message) {
        super(message);
    }

    public RestConnectorException(String message, Throwable cause) {
        super(message, cause);
    }

    public RestConnectorException(Throwable cause) {
        super(cause);
    }

    public RestConnectorException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}