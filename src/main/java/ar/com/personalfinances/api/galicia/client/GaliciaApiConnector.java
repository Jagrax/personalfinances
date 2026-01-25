package ar.com.personalfinances.api.galicia.client;

import ar.com.personalfinances.api.galicia.io.*;
import ar.com.personalfinances.api.galicia.model.Error;
import ar.com.personalfinances.api.galicia.util.Credentials;
import ar.com.personalfinances.service.GaliciaApiService;
import ar.com.personalfinances.util.CmdEncrypt;
import ar.com.personalfinances.util.SimpleCache;
import ar.com.personalfinances.webclient.RestConnector;
import ar.com.personalfinances.webclient.RestConnectorException;
import ar.com.personalfinances.webclient.RestSecurityManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class GaliciaApiConnector implements RestSecurityManager {

    private final SimpleCache galiciaApiConnectorCache = new SimpleCache();
    final String CACHE_KEY_ACCESS_TOKEN = "accessToken";

    private final String documentNumber;
    private final String username;
    private final String password;

    private String cookie;

    public GaliciaApiConnector() {
        this.documentNumber = null;
        this.username = null;
        this.password = null;
    }

    public GaliciaApiConnector(Credentials credentials) {
        this.documentNumber = credentials.getDocumentNumber();
        this.username = credentials.getUsername();
        this.password = credentials.getPassword();
    }

    public GetMovimientosCuentaResponse getMovimientosCuenta(String aspNetSessionId, Date fechaDesde, Date fechaHasta, GaliciaApiService.TipoMovimiento tipoMovimiento, Long pageNumber) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://cuentas.bancogalicia.com.ar", new RestSecurityManager() {
            @Override
            public HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException {
                httpHeaders.add(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.ORIGIN, "https://cuentas.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.REFERER, "https://cuentas.bancogalicia.com.ar/cuentas/mis-cuentas");
                httpHeaders.add(HttpHeaders.COOKIE, "ASP.NET_SessionId=" + aspNetSessionId);
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
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (fechaDesde != null) formData.add("fd", sdf.format(fechaDesde));
        if (fechaHasta != null) formData.add("fh", sdf.format(fechaHasta));
        if (tipoMovimiento != null) formData.add("motivo", tipoMovimiento.getValue());
        if (pageNumber != null) formData.add("pagina", String.valueOf(pageNumber));

        final String path = "/Cuentas/GetMovimientosCuenta";
        log.debug("[getMovimientosCuenta] Request POST por obtener movimientos de la cuenta con request {}", formData);
        return connector.genericPost(path, formData, GetMovimientosCuentaResponse.class, MediaType.APPLICATION_FORM_URLENCODED_VALUE).getFirst();
    }

    public GetMovimientosTarjetaResponse getMovimientosTarjeta(String cookie) throws RestConnectorException {
        this.cookie = cookie;
        final RestConnector connector = new RestConnector("https://tarjetas.bancogalicia.com.ar", this);
        final String path = "/api/consumos/movements";
        log.debug("[getMovimientosTarjeta] Request GET por obtener movimientos de la tarjeta");
        return connector.genericGet(path, GetMovimientosTarjetaResponse.class, ErrorResponse.class).getFirst();
    }

    public PostCardsMovementsResponse postCardsMovements(PostCardsMovementsRequest postCardsMovementsRequest) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://bff-cards-movements-tc-pota-cards.bff.bancogalicia.com.ar", new RestSecurityManager() {
            @Override
            public HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException {
                httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken(false));
                httpHeaders.add(HttpHeaders.HOST, "bff-cards-movements-tc-pota-cards.bff.bancogalicia.com.ar");
                httpHeaders.add("id_channel", "onlinebanking");
                return httpHeaders;
            }

            @Override
            public boolean retryOnUnauthorized() {
                try {
                    getAccessToken(true);
                } catch (RestConnectorException e) {
                    throw new RuntimeException(e);
                }
                return true;
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

    public Pair<String, HttpHeaders> getLoginPage() throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://onlinebanking.bancogalicia.com.ar", new RestSecurityManager() {
            @Override
            public HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException {
                httpHeaders.add(HttpHeaders.HOST, "onlinebanking.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.ORIGIN, "https://onlinebanking.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/login");
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
        final String path = "/login";
        log.trace("[getLoginPage] Request GET por obtener pagina de login");
        return connector.genericGet(path, String.class, null, null, MediaType.TEXT_HTML);
    }

    public Pair<String, HttpHeaders> postLogIn(String loginHeaderCookies, String requestVerificationToken, String encriptedPassword) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://onlinebanking.bancogalicia.com.ar", new RestSecurityManager() {
            @Override
            public HttpHeaders addHeaders(HttpHeaders httpHeaders) throws RestConnectorException {
                httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                httpHeaders.add(HttpHeaders.HOST, "onlinebanking.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.ORIGIN, "https://onlinebanking.bancogalicia.com.ar");
                httpHeaders.add(HttpHeaders.REFERER, "https://onlinebanking.bancogalicia.com.ar/login");
                httpHeaders.add(HttpHeaders.COOKIE, loginHeaderCookies);
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

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("__RequestVerificationToken", requestVerificationToken);
        formData.add("EncriptedPassword", encriptedPassword);
        formData.add("DocumentNumber", documentNumber);
        formData.add("UserName", "0".repeat(username.length()));
        formData.add("Password", "0".repeat(password.length()));
        formData.add("RememberMe", "false");
        formData.add("DevicePrintAdaptive", "version=3.7.1_1&pm_fpua=mozilla/5.0 (windows nt 10.0; win64; x64) applewebkit/537.36 (khtml, like gecko) chrome/143.0.0.0 safari/537.36|5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36|Win32&pm_fpsc=24|1920|1080|1032&pm_fpsw=pdf|pdf|pdf|pdf|pdf&pm_fptz=-3&pm_fpln=lang=en-US|syslang=|userlang=&pm_fpjv=0&pm_fpco=1&pm_fpasw=internal-pdf-viewer|internal-pdf-viewer|internal-pdf-viewer|internal-pdf-viewer|internal-pdf-viewer&pm_fpan=Netscape&pm_fpacn=Mozilla&pm_fpol=true&pm_fposp=&pm_fpup=&pm_fpsaw=1920&pm_fpspd=24&pm_fpsbd=&pm_fpsdx=&pm_fpsdy=&pm_fpslx=&pm_fpsly=&pm_fpsfse=&pm_fpsui=&pm_os=Windows&pm_brmjv=143&pm_br=Chrome&pm_inpt=&pm_expt="/*devicePrintAdaptive*/);
        formData.add("isDebugEnabled", "false");
        formData.add("CodigoProducto", "");

        final String path = "/Users/LogIn";
        log.debug("[postLogIn] Request POST por hacer login con request {}", formData);
        return connector.genericPost(path, formData, String.class, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    private String getAccessToken(boolean force) throws RestConnectorException {
        if (force) galiciaApiConnectorCache.invalidate(CACHE_KEY_ACCESS_TOKEN);
        return (String) galiciaApiConnectorCache.getOrCompute(CACHE_KEY_ACCESS_TOKEN, this::loginAndGetAccessToken);
    }

    private String loginAndGetAccessToken() throws RuntimeException {
        try {
            Pair<String, HttpHeaders> getLoginPageResponseWithHeaders = getLoginPage();
            if (getLoginPageResponseWithHeaders == null) {
                log.error("El request GET de la pagina de login devolvio null");
                throw new RestConnectorException("getLoginPageResponse returns null");
            }

            final String loginPageHtml = getLoginPageResponseWithHeaders.getFirst();
            if (!StringUtils.hasText(loginPageHtml)) {
                log.error("El request GET de la pagina de login devolvio el body/html null o vacio");
                throw new RestConnectorException("getLoginPageResponse returns null or empty body/html");
            }

            Document doc = Jsoup.parse(loginPageHtml);
            Element tokenInput = doc.selectFirst("input[name=__RequestVerificationToken]");
            String csrfToken = tokenInput != null ? tokenInput.attr("value") : null;
            if (!StringUtils.hasText(csrfToken)) {
                log.error("El html de la pagina de login no vino con el input hidden [__RequestVerificationToken]");
                throw new RestConnectorException("__RequestVerificationToken no encontrado");
            }

            // Obtengo los Set-Cookie del response header y los concateno para el siguiente request
            final HttpHeaders responseHeaders = getLoginPageResponseWithHeaders.getSecond();
            String cookies = responseHeaders.getOrEmpty(HttpHeaders.SET_COOKIE).stream().map(setCookie -> {
                String pair = setCookie.split(";", 2)[0]; // name=value
                String[] nv = pair.split("=", 2);
                return nv[0] + "=" + nv[1];
            }).collect(Collectors.joining("; "));


            final Pattern p = Pattern.compile("encryptedString\\(key,\\s*\"([^\"]+)\"\\s*\\+");

            String encryptionSeed = null;

            for (Element script : doc.select("script")) {
                Matcher m = p.matcher(script.html());
                if (m.find()) {
                    encryptionSeed = m.group(1);
                    break;
                }
            }

            if (!StringUtils.hasText(encryptionSeed)) {
                log.error("El html de la pagina de login no vino con el script [encryptedString]");
                throw new RestConnectorException("encryptedString no encontrado");
            }

            final String encryptedPassword = CmdEncrypt.cmdEncrypt(encryptionSeed, username, password);
            if (!StringUtils.hasText(encryptedPassword)) {
                throw new RestConnectorException("La encryptedPassword generada vacia");
            }

            Pair<String, HttpHeaders> postLogInResponse = postLogIn(cookies, csrfToken, encryptedPassword);
            if (postLogInResponse == null) {
                log.error("El request POST de la pagina de login devolvio null");
                throw new RestConnectorException("postLogIn returns null");
            }

            String bearerToken = null;

            HttpHeaders headers = postLogInResponse.getSecond();
            List<String> skywalkerHeader = headers.getOrEmpty("Skywalker");
            if (skywalkerHeader.isEmpty()) {
                for (String setCookie : headers.getOrEmpty(HttpHeaders.SET_COOKIE)) {
                    String pair = setCookie.split(";", 2)[0]; // name=value
                    String[] nv = pair.split("=", 2);
                    if (nv[0].equals("Skywalker")) {
                        bearerToken = nv[1];
                        break;
                    }
                }

                if (!StringUtils.hasText(bearerToken)) {
                    log.info("+--------------------------------------------------------------------------------------------------+");
                    log.info("| Headers");
                    log.info("+--------------------------------------------------------------------------------------------------+");
                    for (String headerKey : headers.keySet()) {
                        log.info("{}: {}", headerKey, headers.get(headerKey));
                    }
                    Document loginFailPage = Jsoup.parse(postLogInResponse.getFirst());
                    log.info("+--------------------------------------------------------------------------------------------------+");
                    log.info("| HTML");
                    log.info("+--------------------------------------------------------------------------------------------------+");
                    log.info(loginFailPage.body().text());

                    log.error("El request POST de la pagina de login devolvio null");
                    throw new RestConnectorException("No se pudo recuperar el bearerToken del response header del login");
                }
            } else {
                bearerToken = skywalkerHeader.get(0);
            }

            log.debug("AccessToken recuperado [{}]", bearerToken);
            return bearerToken;
        } catch (RestConnectorException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public HttpHeaders addHeaders(HttpHeaders httpHeaders) {
        httpHeaders.set(HttpHeaders.COOKIE, cookie);
        return httpHeaders;
    }

    @Override
    public boolean retryOnUnauthorized() {
        try {
            getAccessToken(true);
        } catch (RestConnectorException e) {
            throw new RuntimeException(e);
        }
        return true;
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
}