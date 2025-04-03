package ar.com.personalfinances.webclient;

import org.springframework.http.HttpHeaders;

public interface RestSecurityManager {

    HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException;
}
