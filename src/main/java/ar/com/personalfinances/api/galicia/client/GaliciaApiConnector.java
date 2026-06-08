package ar.com.personalfinances.api.galicia.client;

import ar.com.personalfinances.api.galicia.io.*;
import ar.com.personalfinances.api.galicia.model.Error;
import ar.com.personalfinances.service.GaliciaApiService;
import ar.com.personalfinances.util.DateUtils;
import ar.com.personalfinances.webclient.RestConnector;
import ar.com.personalfinances.webclient.RestConnectorException;
import ar.com.personalfinances.webclient.RestSecurityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class GaliciaApiConnector {

    public GetMovimientosCuentaResponse getMovimientosCuenta(String cuentasCookies, LocalDate fechaDesde, LocalDate fechaHasta, GaliciaApiService.TipoMovimiento tipoMovimiento, Long pageNumber) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://cuentas.bancogalicia.com.ar", new RestSecurityManager() {
            @Override
            public HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException {
                httpHeaders.add(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.ORIGIN, "https://cuentas.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.REFERER, "https://cuentas.bancogalicia.com.ar/cuentas/mis-cuentas");
                httpHeaders.add(HttpHeaders.COOKIE, cuentasCookies);
                return httpHeaders;
            }

            @Override
            public boolean retryOnUnauthorized() {
                return false;
            }

            @Override
            public boolean detectUnauthorized(RestConnectorException e) {
                return false;
            }
        });

        // Este formatter coincide con el formatter de DateUtils (porque es el formato de AR), pero como es especifico de la API, lo quiero tener declarado aca
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (fechaDesde != null) formData.add("fd", DateUtils.format(fechaDesde, formatter));
        if (fechaHasta != null) formData.add("fh", DateUtils.format(fechaHasta, formatter));
        if (tipoMovimiento != null) formData.add("motivo", tipoMovimiento.getValue());
        if (pageNumber != null) formData.add("pagina", String.valueOf(pageNumber));

        final String path = "/Cuentas/GetMovimientosCuenta";
        log.debug("[getMovimientosCuenta] Request POST por obtener movimientos de la cuenta con request {}", formData);
        return connector.genericPost(path, formData, GetMovimientosCuentaResponse.class, MediaType.APPLICATION_FORM_URLENCODED_VALUE).getFirst();
    }

    public PostCardsMovementsResponse postCardsMovements(PostCardsMovementsRequest postCardsMovementsRequest, String cookies) throws RestConnectorException {
        String skywalker = extractSkywalkerFromCookies(cookies);
        if (!StringUtils.hasText(skywalker)) {
            throw new RestConnectorException("Skywalker token not found in cookies");
        }
        final String token = skywalker;
        final RestConnector connector = new RestConnector("https://bff-cards-movements-tc-pota-cards.bff.bancogalicia.com.ar", new RestSecurityManager() {
            @Override
            public HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException {
                httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                httpHeaders.add(HttpHeaders.HOST, "bff-cards-movements-tc-pota-cards.bff.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
                httpHeaders.add(HttpHeaders.ORIGIN, "https://tarjetas.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.REFERER, "https://tarjetas.bancogalicia.com.ar/");
                httpHeaders.add("id_channel", "onlinebanking");
                return httpHeaders;
            }

            @Override
            public boolean retryOnUnauthorized() {
                return false;
            }

            @Override
            public boolean detectUnauthorized(RestConnectorException e) {
                if (e.getEntityError() instanceof PostCardsMovementsResponse) {
                    PostCardsMovementsResponse response = (PostCardsMovementsResponse) e.getEntityError();
                    if (!response.getErrors().isEmpty()) {
                        Error error = response.getErrors().get(0);
                        return "Token expirado".equals(error.getReason());
                    }
                }
                return false;
            }
        });
        final String path = "/bff/cards/movements-tc";
        log.debug("[postCardsMovements] Request POST por obtener movimientos de la tarjeta {}", postCardsMovementsRequest.getBrand());
        return connector.genericPost(path, postCardsMovementsRequest, PostCardsMovementsResponse.class, PostCardsMovementsResponse.class, MediaType.APPLICATION_JSON_VALUE).getFirst();
    }

    public String establishCuentasSession(String onlinebankingCookies) throws RestConnectorException {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            HttpURLConnection.setFollowRedirects(false);
        }});

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, onlinebankingCookies);
        headers.add(HttpHeaders.HOST, "onlinebanking.bancogalicia.com.ar");

        ResponseEntity<String> ssoResponse;
        try {
            ssoResponse = restTemplate.exchange(
                "https://onlinebanking.bancogalicia.com.ar/Navigation/SSOEntryPoint?ReturnUrl=https://cuentas.bancogalicia.com.ar/&appName=Cuentas",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );
            log.info("[establishCuentasSession] SSO response status={}, Location={}", ssoResponse.getStatusCode(), ssoResponse.getHeaders().getFirst(HttpHeaders.LOCATION));
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSession SSO failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSession SSO error: " + e.getMessage(), e);
        }
        if (ssoResponse.getStatusCode() != HttpStatus.FOUND && ssoResponse.getStatusCode() != HttpStatus.MOVED_PERMANENTLY) {
            log.warn("[establishCuentasSession] SSO entry point returned {} (not a redirect). Body: {}", ssoResponse.getStatusCode(), ssoResponse.getBody());
            throw new RestConnectorException("SSO entry point did not return a redirect. Status: " + ssoResponse.getStatusCode());
        }

        String location = ssoResponse.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (!StringUtils.hasText(location)) {
            throw new RestConnectorException("SSO entry point response missing Location header");
        }

        URI ssoLocation;
        try {
            ssoLocation = URI.create(location);
            if (!ssoLocation.isAbsolute()) {
                ssoLocation = new URI("https://cuentas.bancogalicia.com.ar" + (location.startsWith("/") ? location : "/" + location));
            }
        } catch (URISyntaxException e) {
            throw new RestConnectorException("Invalid Location URI from SSO: " + location, e);
        }
        log.info("[establishCuentasSession] Following SSO redirect to: {}", ssoLocation);

        try {
            RestTemplate cuentasRestTemplate = new RestTemplate();
            HttpHeaders cuentasHeaders = new HttpHeaders();
            cuentasHeaders.set(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
            cuentasHeaders.set(HttpHeaders.ORIGIN, "https://cuentas.bancogalicia.com.ar");
            cuentasHeaders.set(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/inicio");
            cuentasHeaders.set(HttpHeaders.COOKIE, onlinebankingCookies);

            ResponseEntity<String> cuentasResponse = cuentasRestTemplate.exchange(
                ssoLocation, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(cuentasHeaders),
                String.class
            );
            log.info("[establishCuentasSession] Cuentas response status={}", cuentasResponse.getStatusCode());

            String cuentasCookies = cuentasResponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream().map(setCookie -> {
                String pair = setCookie.split(";", 2)[0];
                String[] nv = pair.split("=", 2);
                return nv[0] + "=" + nv[1];
            }).collect(Collectors.joining("; "));
            log.info("[establishCuentasSession] Cuentas cookies=[{}]", cuentasCookies);

            if (!StringUtils.hasText(cuentasCookies)) {
                log.info("[establishCuentasSession] No cuentas cookies set; falling back to onlinebanking cookies");
                return onlinebankingCookies;
            }
            return cuentasCookies;
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSession redirect failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSession redirect error: " + e.getMessage(), e);
        }
    }

    public String getCardsOverview(String cookies) throws RestConnectorException {
        String skywalker = extractSkywalkerFromCookies(cookies);
        if (!StringUtils.hasText(skywalker)) {
            throw new RestConnectorException("Skywalker token not found in cookies");
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(skywalker);
            headers.set(HttpHeaders.ACCEPT, "application/vnd.iman.v1+json, application/json, text/plain, */*");
            headers.set("id_channel", "onlinebanking");
            headers.set(HttpHeaders.ORIGIN, "https://tarjetas.bancogalicia.com.ar");
            headers.set(HttpHeaders.REFERER, "https://tarjetas.bancogalicia.com.ar/");
            headers.set(HttpHeaders.HOST, "bff-cards-overview-pota-cards.bff.bancogalicia.com.ar");
            ResponseEntity<String> response = restTemplate.exchange(
                "https://bff-cards-overview-pota-cards.bff.bancogalicia.com.ar/bff/overview/cards",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("getCardsOverview failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("getCardsOverview error: " + e.getMessage(), e);
        }
    }

    public String getSeccionMisCuentas(String onlinebankingCookies) throws RestConnectorException {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                HttpURLConnection.setFollowRedirects(false);
            }});
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "application/json, text/javascript, */*; q=0.01");
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set(HttpHeaders.HOST, "onlinebanking.bancogalicia.com.ar");
            headers.set(HttpHeaders.ORIGIN, "https://onlinebanking.bancogalicia.com.ar");
            headers.set(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/inicio");
            headers.set(HttpHeaders.COOKIE, onlinebankingCookies);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://onlinebanking.bancogalicia.com.ar/Dashboard/GetSeccionMisCuentas",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );
            log.info("[getSeccionMisCuentas] status={}, bodyFirst200=[{}]", response.getStatusCode(),
                response.getBody() != null ? response.getBody().substring(0, Math.min(200, response.getBody().length())) : "null");
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("getSeccionMisCuentas failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("getSeccionMisCuentas error: " + e.getMessage(), e);
        }
    }

    public static String extractSkywalkerFromCookies(String cookies) {
        if (!StringUtils.hasText(cookies)) return null;
        Pattern p = Pattern.compile("Skywalker\\s*=\\s*([^;]+)");
        Matcher m = p.matcher(cookies);
        return m.find() ? m.group(1).trim() : null;
    }
}
