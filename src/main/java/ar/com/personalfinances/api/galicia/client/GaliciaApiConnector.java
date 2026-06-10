package ar.com.personalfinances.api.galicia.client;

import ar.com.personalfinances.api.galicia.io.GetMovimientosCuentaResponse;
import ar.com.personalfinances.api.galicia.io.PostCardsMovementsRequest;
import ar.com.personalfinances.api.galicia.io.PostCardsMovementsResponse;
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });

        // Step 1: GET showexternal/210 to obtain tokenML
        HttpHeaders obHeaders = new HttpHeaders();
        obHeaders.add(HttpHeaders.COOKIE, onlinebankingCookies);
        obHeaders.add(HttpHeaders.HOST, "onlinebanking.bancogalicia.com.ar");
        obHeaders.add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
        obHeaders.add(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        obHeaders.add(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,es;q=0.8");
        obHeaders.add(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/inicio");

        ResponseEntity<String> showExternalResponse;
        try {
            showExternalResponse = restTemplate.exchange(
                "https://onlinebanking.bancogalicia.com.ar/navigation/showexternal/210",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(obHeaders),
                String.class
            );
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSession showexternal failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSession showexternal error: " + e.getMessage(), e);
        }
        String showExternalHtml = showExternalResponse.getBody();
        log.trace("[establishCuentasSession] showexternal status={}, bodyLength={}", showExternalResponse.getStatusCode(),
            showExternalHtml != null ? showExternalHtml.length() : 0);

        if (showExternalHtml == null || showExternalHtml.isEmpty()) {
            throw new RestConnectorException("establishCuentasSession: showexternal returned empty body");
        }

        // Step 2: Extract tokenML from HTML
        String tokenML = null;
        Pattern tokenPattern = Pattern.compile("name=\"tokenML\"\\s+value=\"([^\"]+)\"");
        Matcher matcher = tokenPattern.matcher(showExternalHtml);
        if (matcher.find()) {
            tokenML = matcher.group(1);
        }
        if (!StringUtils.hasText(tokenML)) {
            // Try alternative pattern: input with tokenML in a form
            tokenPattern = Pattern.compile("value=\"([^\"]+)\"[^>]*name=\"tokenML\"");
            matcher = tokenPattern.matcher(showExternalHtml);
            if (matcher.find()) {
                tokenML = matcher.group(1);
            }
        }
        if (!StringUtils.hasText(tokenML)) {
            log.warn("[establishCuentasSession] Could not find tokenML in showexternal response. bodySnippet=[{}]",
                showExternalHtml.substring(0, Math.min(1000, showExternalHtml.length())));
            throw new RestConnectorException("establishCuentasSession: tokenML not found in showexternal response");
        }
        log.trace("[establishCuentasSession] Found tokenML (first 100 chars)=[{}]", tokenML.substring(0, Math.min(100, tokenML.length())));

        // Step 3: POST tokenML to cuentas SSO entry point
        HttpHeaders ssoHeaders = new HttpHeaders();
        ssoHeaders.set(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
        ssoHeaders.set(HttpHeaders.ORIGIN, "https://onlinebanking.bancogalicia.com.ar");
        ssoHeaders.set(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/");
        ssoHeaders.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
        ssoHeaders.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        ssoHeaders.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,es;q=0.8");
        ssoHeaders.set(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
        // Browser sends NO cookies with this cross-domain SSO POST

        String ssoBody = "tokenML=" + java.net.URLEncoder.encode(tokenML, java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity<String> ssoResponse;
        try {
            ssoResponse = restTemplate.exchange(
                "https://cuentas.bancogalicia.com.ar/Navigation/SSOEntryPoint",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(ssoBody, ssoHeaders),
                String.class
            );
            log.trace("[establishCuentasSession] SSO POST response status={}, Location={}, bodyLength={}",
                ssoResponse.getStatusCode(), ssoResponse.getHeaders().getFirst(HttpHeaders.LOCATION),
                ssoResponse.getBody() != null ? ssoResponse.getBody().length() : 0);
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSession SSO POST failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSession SSO POST error: " + e.getMessage(), e);
        }

        if (ssoResponse.getStatusCode() != HttpStatus.FOUND && ssoResponse.getStatusCode() != HttpStatus.MOVED_PERMANENTLY) {
            throw new RestConnectorException("establishCuentasSession SSO POST did not return a redirect. Status: " + ssoResponse.getStatusCode());
        }

        // Step 4: Follow redirect to MenuLink/210
        String menuLinkLocation = ssoResponse.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (!StringUtils.hasText(menuLinkLocation)) {
            throw new RestConnectorException("establishCuentasSession SSO POST missing Location header");
        }
        URI menuLinkUri = URI.create(menuLinkLocation);
        if (!menuLinkUri.isAbsolute()) {
            try { menuLinkUri = new URI("https://cuentas.bancogalicia.com.ar" + (menuLinkLocation.startsWith("/") ? menuLinkLocation : "/" + menuLinkLocation)); }
            catch (URISyntaxException e) { throw new RestConnectorException("Invalid MenuLink URI: " + menuLinkLocation, e); }
        }
        log.trace("[establishCuentasSession] Following SSO redirect to MenuLink: {}", menuLinkUri);

        // Extract ALL cuentas-domain cookies from SSO POST Set-Cookie.
        // MenuLink only needs ASP.NET_SessionId, but subsequent API calls (getMovimientosCuenta)
        // need all cookies (ADRUM_*, TS010dd3b2, etc.) for the load-balanced session.
        String ssoCookies = ssoResponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
            .map(setCookie -> setCookie.split(";", 2)[0])
            .filter(pair -> pair.contains("="))
            .collect(Collectors.joining("; "));
        if (!StringUtils.hasText(ssoCookies) || !ssoCookies.contains("ASP.NET_SessionId=")) {
            throw new RestConnectorException("establishCuentasSession: SSO POST did not set ASP.NET_SessionId in cookies");
        }
        log.trace("[establishCuentasSession] ssoCookies=[{}]", ssoCookies);

        // Browser sends only basic headers (NO Origin, NO onlinebanking cookies) for navigation redirects
        HttpHeaders cuentasHeaders = new HttpHeaders();
        cuentasHeaders.set(HttpHeaders.COOKIE, ssoCookies);
        cuentasHeaders.set(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/");
        cuentasHeaders.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
        cuentasHeaders.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        cuentasHeaders.set("Upgrade-Insecure-Requests", "1");

        ResponseEntity<String> menuLinkResponse;
        try {
            menuLinkResponse = restTemplate.exchange(menuLinkUri, org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(cuentasHeaders), String.class);
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSession MenuLink failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSession MenuLink error: " + e.getMessage(), e);
        }
        log.trace("[establishCuentasSession] MenuLink response status={}, Location={}", menuLinkResponse.getStatusCode(),
            menuLinkResponse.getHeaders().getFirst(HttpHeaders.LOCATION));

        if (menuLinkResponse.getStatusCode() != HttpStatus.FOUND && menuLinkResponse.getStatusCode() != HttpStatus.MOVED_PERMANENTLY) {
            throw new RestConnectorException("establishCuentasSession MenuLink did not return a redirect. Status: " + menuLinkResponse.getStatusCode());
        }

        // Step 5: Follow redirect to cuentas/inicio
        String cuentasInicioLocation = menuLinkResponse.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (!StringUtils.hasText(cuentasInicioLocation)) {
            throw new RestConnectorException("establishCuentasSession MenuLink missing Location header");
        }
        URI cuentasInicioUri = URI.create(cuentasInicioLocation);
        if (!cuentasInicioUri.isAbsolute()) {
            try { cuentasInicioUri = new URI("https://cuentas.bancogalicia.com.ar" + (cuentasInicioLocation.startsWith("/") ? cuentasInicioLocation : "/" + cuentasInicioLocation)); }
            catch (URISyntaxException e) { throw new RestConnectorException("Invalid cuentas/inicio URI: " + cuentasInicioLocation, e); }
        }
        log.trace("[establishCuentasSession] Following MenuLink redirect to cuentas/inicio: {}", cuentasInicioUri);

        ResponseEntity<String> cuentasInicioResponse;
        try {
            cuentasInicioResponse = restTemplate.exchange(cuentasInicioUri, org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(cuentasHeaders), String.class);
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSession cuentas/inicio failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSession cuentas/inicio error: " + e.getMessage(), e);
        }
        String cuentasHtml = cuentasInicioResponse.getBody();
        log.trace("[establishCuentasSession] cuentas/inicio status={}, bodyLength={}, bodyStartsWith=[{}]",
            cuentasInicioResponse.getStatusCode(),
            cuentasHtml != null ? cuentasHtml.length() : 0,
            cuentasHtml != null ? cuentasHtml.substring(0, Math.min(500, cuentasHtml.length())) : "null");

        // Return only cuentas cookies (NOT onlinebanking cookies - browser doesn't send cross-domain)
        String cuentasSetCookies = cuentasInicioResponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
            .map(setCookie -> setCookie.split(";", 2)[0])
            .filter(pair -> pair.contains("="))
            .map(pair -> pair.split("=", 2))
            .map(nv -> nv[0] + "=" + nv[1])
            .collect(Collectors.joining("; "));
        log.trace("[establishCuentasSession] cuentas/inicio Set-Cookie headers=[{}]", cuentasSetCookies);

        // Return all cuentas cookies (SSO cookies + any new ones from cuentas/inicio)
        if (StringUtils.hasText(cuentasSetCookies)) {
            return ssoCookies + "; " + cuentasSetCookies;
        }
        return ssoCookies;
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

    public String establishCuentasSessionDirect(String onlinebankingCookies) throws RestConnectorException {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                HttpURLConnection.setFollowRedirects(false);
            }});
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "es-AR,es;q=0.9,en;q=0.8");
            headers.set(HttpHeaders.COOKIE, onlinebankingCookies);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://cuentas.bancogalicia.com.ar/",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );
            log.info("[establishCuentasSessionDirect] status={}, Location={}", response.getStatusCode(),
                response.getHeaders().getFirst(HttpHeaders.LOCATION));

            String cuentasSetCookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .map(setCookie -> setCookie.split(";", 2)[0])
                .filter(pair -> pair.contains("="))
                .map(pair -> pair.split("=", 2))
                .map(nv -> nv[0] + "=" + nv[1])
                .collect(Collectors.joining("; "));
            log.info("[establishCuentasSessionDirect] cuentasSetCookies=[{}]", cuentasSetCookies);

            String mergedCookies;
            if (StringUtils.hasText(cuentasSetCookies)) {
                mergedCookies = onlinebankingCookies + "; " + cuentasSetCookies;
            } else {
                mergedCookies = onlinebankingCookies;
            }
            return mergedCookies;
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("establishCuentasSessionDirect failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("establishCuentasSessionDirect error: " + e.getMessage(), e);
        }
    }

    public String getCuentasInicioPage(String cuentasCookies) throws RestConnectorException {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            headers.set(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
            headers.set(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/");
            headers.set(HttpHeaders.COOKIE, cuentasCookies);
            headers.set("Upgrade-Insecure-Requests", "1");
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
            ResponseEntity<String> response = restTemplate.exchange(
                "https://cuentas.bancogalicia.com.ar/cuentas/inicio",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );
            String body = response.getBody();
            log.trace("[getCuentasInicioPage] status={}, bodyLength={}, bodyStartsWith=[{}]", response.getStatusCode(),
                body != null ? body.length() : 0,
                body != null ? body.substring(0, Math.min(2000, body.length())) : "null");
            return body;
        } catch (HttpStatusCodeException e) {
            throw new RestConnectorException("getCuentasInicioPage failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RestConnectorException("getCuentasInicioPage error: " + e.getMessage(), e);
        }
    }

    public String selectAccount(String cuentasCookies, String accountTipo, String accountIndex) throws RestConnectorException {
        String perfilPage = getPerfilPageName(accountTipo);
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            headers.set(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
            headers.set(HttpHeaders.REFERER, "https://cuentas.bancogalicia.com.ar/cuentas/inicio");
            headers.set(HttpHeaders.COOKIE, cuentasCookies);
            headers.set("Upgrade-Insecure-Requests", "1");
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");

            String url = "https://cuentas.bancogalicia.com.ar/cuentas/" + perfilPage + "?index=" + accountIndex + "&origen=indiceCuentas";
            log.info("[selectAccount] Selecting account via GET {}", url);

            ResponseEntity<String> response;
            try {
                response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    String.class
                );
            } catch (HttpStatusCodeException e) {
                throw new RestConnectorException("selectAccount failed: " + e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusCode(), e.getResponseBodyAsString());
            } catch (RestClientException e) {
                throw new RestConnectorException("selectAccount error: " + e.getMessage(), e);
            }

            int statusCode = response.getStatusCode().value();
            log.info("[selectAccount] Response status={}, Location={}", statusCode,
                response.getHeaders().getFirst(HttpHeaders.LOCATION));

            if (statusCode >= 400) {
                throw new RestConnectorException("selectAccount: unexpected status " + statusCode,
                    statusCode, response.getStatusCode(), "Status " + statusCode);
            }

            // Capture any new cookies from the response (including after redirect)
            String newCookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .map(setCookie -> setCookie.split(";", 2)[0])
                .filter(pair -> pair.contains("="))
                .collect(Collectors.joining("; "));

            // Merge original cookies with any new ones
            String mergedCookies = cuentasCookies;
            if (StringUtils.hasText(newCookies)) {
                mergedCookies = mergeCookies(cuentasCookies, newCookies);
            }

            log.info("[selectAccount] Account selected successfully: {} for {}, cookies updated={}", perfilPage, accountIndex, StringUtils.hasText(newCookies));
            return mergedCookies;
        } catch (RestConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new RestConnectorException("selectAccount error: " + e.getMessage(), e);
        }
    }

    private String mergeCookies(String existingCookies, String newCookies) {
        Map<String, String> cookieMap = new LinkedHashMap<>();
        if (StringUtils.hasText(existingCookies)) {
            for (String pair : existingCookies.split(";")) {
                String[] parts = pair.trim().split("=", 2);
                if (parts.length == 2) {
                    cookieMap.put(parts[0], parts[1]);
                }
            }
        }
        if (StringUtils.hasText(newCookies)) {
            for (String pair : newCookies.split(";")) {
                String[] parts = pair.trim().split("=", 2);
                if (parts.length == 2) {
                    cookieMap.put(parts[0], parts[1]);
                }
            }
        }
        return cookieMap.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("; "));
    }

    private static final Map<String, String> TIPO_TO_PERFIL = new HashMap<>();
    static {
        TIPO_TO_PERFIL.put("CA", "PerfilCA");
        TIPO_TO_PERFIL.put("CC", "PerfilCC");
        TIPO_TO_PERFIL.put("USD", "PerfilUSD");
        TIPO_TO_PERFIL.put("US", "PerfilUSD");
    }

    private String getPerfilPageName(String tipo) {
        String perfil = TIPO_TO_PERFIL.get(tipo.toUpperCase());
        if (perfil != null) return perfil;
        log.warn("[getPerfilPageName] Unknown account tipo [{}], defaulting to PerfilCA", tipo);
        return "PerfilCA";
    }

    public static String extractSkywalkerFromCookies(String cookies) {
        if (!StringUtils.hasText(cookies)) return null;
        Pattern p = Pattern.compile("Skywalker\\s*=\\s*([^;]+)");
        Matcher m = p.matcher(cookies);
        return m.find() ? m.group(1).trim() : null;
    }
}
