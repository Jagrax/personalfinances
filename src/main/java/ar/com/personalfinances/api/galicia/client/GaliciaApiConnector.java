package ar.com.personalfinances.api.galicia.client;

import ar.com.personalfinances.api.galicia.io.*;
import ar.com.personalfinances.service.GaliciaApiService;
import ar.com.personalfinances.webclient.RestConnector;
import ar.com.personalfinances.webclient.RestConnectorException;
import ar.com.personalfinances.webclient.RestSecurityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class GaliciaApiConnector implements RestSecurityManager {

    private String cookie;

    public GetMovimientosCuentaResponse getMovimientosCuenta(String cookie, Date fechaDesde, Date fechaHasta, GaliciaApiService.TipoMovimiento tipoMovimiento, Long pageNumber) throws RestConnectorException {
        this.cookie = cookie;
        final RestConnector connector = new RestConnector("https://cuentas.bancogalicia.com.ar", this);
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (fechaDesde != null) formData.add("fd", sdf.format(fechaDesde));
        if (fechaHasta != null) formData.add("fh", sdf.format(fechaHasta));
        if (tipoMovimiento != null) formData.add("motivo", tipoMovimiento.getValue());
        if (pageNumber != null) formData.add("pagina", String.valueOf(pageNumber));

        final String path = "/Cuentas/GetMovimientosCuenta";
        log.debug("[getMovimientosCuenta] Request POST por obtener movimientos de la cuenta con request {}", formData);
        return connector.genericPost(path, formData, GetMovimientosCuentaResponse.class, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    public GetMovimientosTarjetaResponse getMovimientosTarjeta(String cookie) throws RestConnectorException {
        this.cookie = cookie;
        final RestConnector connector = new RestConnector("https://tarjetas.bancogalicia.com.ar", this);
        final String path = "/api/consumos/movements";
        log.debug("[getMovimientosTarjeta] Request GET por obtener movimientos de la tarjeta");
        return connector.genericGet(path, GetMovimientosTarjetaResponse.class, ErrorResponse.class);
    }

    public PostCardsMovementsResponse postCardsMovements(String bearerToken, PostCardsMovementsRequest postCardsMovementsRequest) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://bff-cards-movements-tc-pota-cards.bff.bancogalicia.com.ar", httpHeaders -> {
            httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            httpHeaders.add(HttpHeaders.HOST, "bff-cards-movements-tc-pota-cards.bff.bancogalicia.com.ar");
            httpHeaders.add("id_channel", "onlinebanking");
            return httpHeaders;
        });
        final String path = "/bff/cards/movements-tc";
        log.debug("[postCardsMovements] Request POST por obtener movimientos de la tarjeta {}", postCardsMovementsRequest.getBrand());
        return connector.genericPost(path, postCardsMovementsRequest, PostCardsMovementsResponse.class, MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    public HttpHeaders addHeaders(HttpHeaders httpHeaders) {
        httpHeaders.set(HttpHeaders.COOKIE, cookie);
        return httpHeaders;
    }
}