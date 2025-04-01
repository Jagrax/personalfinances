package ar.com.personalfinances.webclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Getter
public class RestConnector {

    private final String baseUrl;

    public RestConnector(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public <Res, Err> Res genericGet(String path, Class<Res> responseType, Class<Err> errorType, Map<String, String> headers) throws RestConnectorException {
        return internalGenericRequest(HttpMethod.GET, path, null, responseType, errorType, headers, MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON);
    }

    public <Res, Req> Res genericPost(String path, Req requestBody, Class<Res> responseType, Map<String, String> headers, String postMediaType) throws RestConnectorException {
        return internalGenericRequest(HttpMethod.POST, path, requestBody, responseType, null, headers, postMediaType, MediaType.APPLICATION_JSON);
    }

    public <Req, Res, Err> Res internalGenericRequest(HttpMethod method, String path, Req requestBody, Class<Res> responseType, Class<Err> errorType, Map<String, String> headers, String postMediaType, MediaType acceptMediaType) throws RestConnectorException {
        // Crear el RestTemplate
        RestTemplate restTemplate = new RestTemplate();

        // Crear los headers para la solicitud
        HttpHeaders httpHeaders = new HttpHeaders();

        // Agregar los headers personalizados
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                httpHeaders.set(header.getKey(), header.getValue());
            }
        }
        if (postMediaType != null) {
            httpHeaders.set(HttpHeaders.CONTENT_TYPE, postMediaType);
        }
        if (acceptMediaType != null) {
            httpHeaders.set(HttpHeaders.ACCEPT, acceptMediaType.toString());
        }

        // Crear la entidad con el cuerpo de la solicitud, si existe
        HttpEntity<Req> entity = new HttpEntity<>(requestBody, httpHeaders);

        // Realizar la solicitud (GET, POST, etc.)
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(getBaseUrl() + path, method, entity, String.class);
            checkSuccessfulOrException(responseEntity, errorType);
            return processResponse(responseEntity.getBody(), responseType);
        } catch (RestConnectorException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            convertErrorEntityAsStringToErrorTypeAndThrowRestConnectorException(e.getResponseBodyAsString(), errorType, e.getStatusCode());
            return null;  // Este return nunca se ejecuta porque `convertErrorEntityAsStringToErrorTypeAndThrowRestConnectorException` siempre lanza una excepción.
        } catch (Exception e) {
            // Manejar posibles excepciones, como un error de red
            throw new RestConnectorException("Error realizando la solicitud HTTP: " + e.getMessage(), e);
        }
    }

    private <Err> void checkSuccessfulOrException(ResponseEntity<String> response, Class<Err> errorType) throws RestConnectorException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            convertErrorEntityAsStringToErrorTypeAndThrowRestConnectorException(response.getBody(), errorType, response.getStatusCode());
        }
    }

    private <Err> void convertErrorEntityAsStringToErrorTypeAndThrowRestConnectorException(String errorEntityAsString, Class<Err> errorType, HttpStatus status) throws RestConnectorException {
        Object entityResponseError;

        if (errorType != null) {
            try {
                // Usamos ObjectMapper para deserializar el String a un objeto del tipo errorType
                ObjectMapper objectMapper = new ObjectMapper();
                entityResponseError = objectMapper.readValue(errorEntityAsString, errorType);
                log.trace("[RestConnector] Entity responseError [{}]", entityResponseError);
            } catch (Exception e) {
                //Pordria haber tenido un problema de tupos al intentar leer la responde como un objeto
                //Traro de leerlo de otra forma, pero igual es un error
                entityResponseError = errorEntityAsString;
                log.debug("La consulta no tiene tipo [{}]", errorType.getCanonicalName());
            }
        } else {
            entityResponseError = errorEntityAsString;
        }

        throw new RestConnectorException("La consulta no devolvio OK. Status: [" + status.value() + "/" + status.getReasonPhrase() + "]", status.value(), status, entityResponseError);
    }

    private <Res> Res processResponse(String responseBody, Class<Res> responseType) throws RestConnectorException {
        Res entityResponse;
        if (!StringUtils.hasText(responseBody) || responseType == Void.class) {
            entityResponse = null;
            log.trace("[RestConnector] Entity response [vacio]");
        } else {
            try {
                // Usamos ObjectMapper para deserializar el String a un objeto del tipo responseType
                ObjectMapper objectMapper = new ObjectMapper();
                entityResponse = objectMapper.readValue(responseBody, responseType);
                log.trace("[RestConnector] Entity response [{}]", entityResponse);
            } catch (Exception e) {
                //Pordria haber tenido un problema de tupos al intentar leer la responde como un objeto
                //Traro de leerlo de otra forma, pero igual es un error
                throw new RestConnectorException("La consulta no tiene tipo [" + responseType.getCanonicalName() + "]. Resultado: [" + responseBody + "]", e);
            }
        }
        return entityResponse;
    }
}