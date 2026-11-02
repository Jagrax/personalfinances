package ar.com.personalfinances.webclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Getter
public class RestConnector {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final RestSecurityManager securityManager;

    public RestConnector(String baseUrl, RestSecurityManager securityManager) {
        this.baseUrl = baseUrl;
        this.securityManager = securityManager;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ------------------------- [GET] -------------------------

    public <Res, Err> Pair<Res, HttpHeaders> genericGet(String path, Class<Res> responseType, Class<Err> errorType) throws RestConnectorException {
        return internalGenericRequest(HttpMethod.GET, path, null, responseType, errorType, MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON);
    }

    public <Res, Err> Pair<Res, HttpHeaders> genericGet(String path, Class<Res> responseType, Class<Err> errorType, String postMediaType, MediaType acceptMediaType) throws RestConnectorException {
        return internalGenericRequest(HttpMethod.GET, path, null, responseType, errorType, postMediaType, acceptMediaType);
    }

    // ------------------------- [POST] -------------------------

    public <Res, Req> Pair<Res, HttpHeaders> genericPost(String path, Req requestBody, Class<Res> responseType, String postMediaType) throws RestConnectorException {
        return internalGenericRequest(HttpMethod.POST, path, requestBody, responseType, null, postMediaType, MediaType.APPLICATION_JSON);
    }

    public <Res, Req, Err> Pair<Res, HttpHeaders> genericPost(String path, Req requestBody, Class<Res> responseType, Class<Err> errorType, String postMediaType) throws RestConnectorException {
        return internalGenericRequest(HttpMethod.POST, path, requestBody, responseType, errorType, postMediaType, MediaType.APPLICATION_JSON);
    }

    // Wrapper para poder hacer el reintento si falla la autorizacion
    private  <Req, Res, Err> Pair<Res, HttpHeaders> internalGenericRequest(HttpMethod method, String path, Req requestBody, Class<Res> responseType, Class<Err> errorType, String acceptMediaType, MediaType postMediaType) throws RestConnectorException {
        try {
            return internalGenericRequestNoTryOnAuth(method, path, requestBody, responseType, errorType, acceptMediaType, postMediaType);
        } catch (RestConnectorException e) {
            if ((HttpStatus.UNAUTHORIZED.equals(e.getStatusInfo()) || securityManager.detectUnauthorized(e)) && securityManager.retryOnUnauthorized()) {
                log.error("Retry por autorizacion al hacer GET para request [{}] con path [{}]. Se reconsulta", requestBody, path);
                return internalGenericRequestNoTryOnAuth(method, path, requestBody, responseType, errorType, acceptMediaType, postMediaType);
            } else {
                throw e;
            }
        }
    }

    public <Req, Res, Err> Pair<Res, HttpHeaders> internalGenericRequestNoTryOnAuth(HttpMethod method, String path, Req requestBody, Class<Res> responseType, Class<Err> errorType, String postMediaType, MediaType acceptMediaType) throws RestConnectorException {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders httpHeaders = new HttpHeaders();

        if (securityManager != null) {
            httpHeaders = securityManager.addHeaders(httpHeaders);
        }
        if (postMediaType != null) {
            httpHeaders.set(HttpHeaders.CONTENT_TYPE, postMediaType);
        }
        if (acceptMediaType != null) {
            httpHeaders.set(HttpHeaders.ACCEPT, acceptMediaType.toString());
        }

        HttpEntity<Req> entity = new HttpEntity<>(requestBody, httpHeaders);

        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(getBaseUrl() + path, method, entity, String.class);
            checkSuccessfulOrException(responseEntity, errorType);
            return Pair.of(processResponse(responseEntity.getBody(), responseType), responseEntity.getHeaders());
        } catch (RestConnectorException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            processErrorAndThrowRestConnectorException(e.getResponseBodyAsString(), errorType, (HttpStatus) e.getStatusCode());
            return null;  // Inalcanzable: processErrorAndThrowRestConnectorException siempre lanza una excepcion.
        } catch (Exception e) {
            throw new RestConnectorException("Error realizando la solicitud HTTP: " + e.getMessage(), e);
        }
    }

    private <Err> void checkSuccessfulOrException(ResponseEntity<String> response, Class<Err> errorType) throws RestConnectorException {
        final HttpStatusCode statusCode = response.getStatusCode();
        if (!(statusCode.is2xxSuccessful() || statusCode.is3xxRedirection())) {
            processErrorAndThrowRestConnectorException(response.getBody(), errorType, (HttpStatus) statusCode);
        }
    }

    private <Err> void processErrorAndThrowRestConnectorException(String errorEntityAsString, Class<Err> errorType, HttpStatusCode status) throws RestConnectorException {
        Object entityResponseError;

        if (errorType != null) {
            try {
                entityResponseError = objectMapper.readValue(errorEntityAsString, errorType);
                log.trace("[RestConnector] Entity responseError [{}]", entityResponseError);
            } catch (Exception e) {
                // El error no deserializa al tipo esperado: se propaga el body crudo
                entityResponseError = errorEntityAsString;
                log.debug("La consulta no tiene tipo [{}]", errorType.getCanonicalName());
            }
        } else {
            entityResponseError = errorEntityAsString;
        }

        throw new RestConnectorException("La consulta no devolvio OK. Status: [" + status.value() + "/" + ((HttpStatus) status).getReasonPhrase() + "]", status.value(), status, entityResponseError);
    }

    private <Res> Res processResponse(String responseBody, Class<Res> responseType)
            throws RestConnectorException {

        if (!StringUtils.hasText(responseBody) || responseType == Void.class) {
            log.trace("[RestConnector] Entity response [vacio]");
            return null;
        }

        if (responseType == String.class) {
            log.trace("[RestConnector] Entity response [String]");
            @SuppressWarnings("unchecked")
            Res res = (Res) responseBody;
            return res;
        }

        try {
            Res entityResponse = objectMapper.readValue(responseBody, responseType);
            log.trace("[RestConnector] Entity response [{}]", entityResponse);
            return entityResponse;
        } catch (Exception e) {
            throw new RestConnectorException("La consulta no tiene tipo [" + responseType.getCanonicalName() +"]. Resultado: [" + responseBody + "]", e);
        }
    }
}