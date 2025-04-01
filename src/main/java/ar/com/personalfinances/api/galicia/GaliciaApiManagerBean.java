package ar.com.personalfinances.api.galicia;

import ar.com.personalfinances.api.galicia.client.GaliciaApiConnector;
import ar.com.personalfinances.api.galicia.io.ErrorResponse;
import ar.com.personalfinances.api.galicia.io.GetMovimientosCuentaResponse;
import ar.com.personalfinances.api.galicia.io.GetMovimientosTarjetaResponse;
import ar.com.personalfinances.api.galicia.model.Data;
import ar.com.personalfinances.api.galicia.model.Model;
import ar.com.personalfinances.api.galicia.model.Movimiento;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.webclient.RestConnectorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class GaliciaApiManagerBean implements GaliciaApiManager {

    @Override
    public CommonResult getMovimientosCuenta(String cookies, Date from, Date to) {
        final List<Movimiento> movimientos = new ArrayList<>();
        long actualPage = 0;
        Long totalPaginas = null;

        GetMovimientosCuentaResponse getMovimientosCuentaResponse;
        while (totalPaginas == null || actualPage < totalPaginas) {
            try {
                log.info("[getMovimientosCuenta] Por consultar los movimientos de la cuenta para las fechas [{} | {}]. Pagina actual: {}", from, to, actualPage);
                getMovimientosCuentaResponse = new GaliciaApiConnector().getMovimientosCuenta(cookies, from, to, TipoMovimiento.TODOS, actualPage);
            } catch (RestConnectorException e) {
                return logAndReturnError("getMovimientosCuenta", e);
            }

            if (getMovimientosCuentaResponse == null) {
                return CommonResult.error("[getMovimientosCuenta] Error al consultar los movimientos de la cuenta para las fechas [" + from + " | " + to + "] con pagina [" + actualPage + "] devolvio null");
            } else {
                Model model = getMovimientosCuentaResponse.getModel();
                if (totalPaginas == null) totalPaginas = model.getTotalPaginas();
                movimientos.addAll(model.getMovimientos());
                actualPage++;
            }
        }

        log.info("[getMovimientosCuenta] Consulta de movimientos de la cuenta para las fechas [{} | {}] finalizada. Movimientos recuperados: {}", from, to, movimientos.size());
        return CommonResult.ok(movimientos);
    }

    @Override
    public CommonResult getMovimientosTarjeta(String cookies) {
        GetMovimientosTarjetaResponse getMovimientosTarjetaResponse;
        try {
            log.info("[getMovimientosTarjeta] Por consultar los movimientos de la tarjeta");
            getMovimientosTarjetaResponse = new GaliciaApiConnector().getMovimientosTarjeta(cookies);
        } catch (RestConnectorException e) {
            return logAndReturnError("getMovimientosTarjeta", e);
        }

        if (getMovimientosTarjetaResponse == null) {
            return CommonResult.error("getMovimientosCuenta returns null");
        }

        if (getMovimientosTarjetaResponse.getErrores() != null && !getMovimientosTarjetaResponse.getErrores().isEmpty()) {
            return CommonResult.error("getMovimientosCuenta returns error");
        } else {
            Data data = getMovimientosTarjetaResponse.getData();
            log.info("[getMovimientosTarjeta] Consulta de movimientos de la tarjeta finalizada. Movimientos recuperados: {}", data.getMovements().size());
            return CommonResult.ok(data.getMovements());
        }
    }

    private CommonResult logAndReturnError(String tag, RestConnectorException e) {
        boolean logError = true;
        String logMessage = "[" + tag + "] Error de Galicia";
        String msgDetail;
        if (e.getEntityError() != null && e.getEntityError() instanceof ErrorResponse) {
            ErrorResponse commonError = (ErrorResponse) e.getEntityError();
            msgDetail = commonError.getMessage();
            logMessage += " " + commonError;
        } else if (e.getStatusInfo().equals(HttpStatus.FOUND) && e.getEntityError() != null && ((String) e.getEntityError()).contains("sesionexpirada")) {
            msgDetail = "Sesion expirada (cookie invalida)";
            logError = false;
        } else {
            msgDetail = e.getCause().getMessage();
        }
        if (logError) log.error(logMessage, e);
        return CommonResult.error("Error de Galicia [" + msgDetail + "]");
    }
}