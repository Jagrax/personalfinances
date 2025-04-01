package ar.com.personalfinances.api.galicia.client;

import ar.com.personalfinances.api.galicia.GaliciaApiManager;
import ar.com.personalfinances.api.galicia.io.ErrorResponse;
import ar.com.personalfinances.api.galicia.io.GetMovimientosCuentaResponse;
import ar.com.personalfinances.api.galicia.io.GetMovimientosTarjetaResponse;
import ar.com.personalfinances.webclient.RestConnector;
import ar.com.personalfinances.webclient.RestConnectorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Slf4j
public class GaliciaApiConnector {

    public GetMovimientosCuentaResponse getMovimientosCuenta(String cookie, Date fechaDesde, Date fechaHasta, GaliciaApiManager.TipoMovimiento tipoMovimiento, Long pageNumber) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://cuentas.bancogalicia.com.ar");
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (fechaDesde != null) formData.add("fd", sdf.format(fechaDesde));
        if (fechaHasta != null) formData.add("fh", sdf.format(fechaHasta));
        if (tipoMovimiento != null) formData.add("motivo", tipoMovimiento.getValue());
        if (pageNumber != null) formData.add("pagina", String.valueOf(pageNumber));

        final String path = "/Cuentas/GetMovimientosCuenta";
        final Map<String, String> headers = Map.of(HttpHeaders.COOKIE, cookie);
        log.debug("[getMovimientosCuenta] Request POST por obtener movimientos de la cuenta con request {}", formData);
        return connector.genericPost(path, formData, GetMovimientosCuentaResponse.class, headers, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    public GetMovimientosTarjetaResponse getMovimientosTarjeta(String cookie) throws RestConnectorException {
        final RestConnector connector = new RestConnector("https://tarjetas.bancogalicia.com.ar");
        final String path = "/api/consumos/movements";
        log.debug("[getMovimientosTarjeta] Request GET por obtener movimientos de la tarjeta");
        return connector.genericGet(path, GetMovimientosTarjetaResponse.class, ErrorResponse.class, Map.of(HttpHeaders.COOKIE, cookie));
    }
}