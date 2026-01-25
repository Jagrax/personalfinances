package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.client.GaliciaApiConnector;
import ar.com.personalfinances.api.galicia.io.*;
import ar.com.personalfinances.api.galicia.model.*;
import ar.com.personalfinances.api.galicia.util.Credentials;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.webclient.RestConnectorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class GaliciaApiServiceImpl implements GaliciaApiService {

    @Override
    public CommonResult getMovimientosCuenta(String aspNetSessionId, Date from, Date to) {
        final List<BankAccountMovement> movimientos = new ArrayList<>();
        long actualPage = 0;
        Long totalPaginas = null;

        GetMovimientosCuentaResponse getMovimientosCuentaResponse;
        while (totalPaginas == null || actualPage < totalPaginas) {
            try {
                log.info("[getMovimientosCuenta] Por consultar los movimientos de la cuenta para las fechas [{} | {}]. Pagina actual: {}", from, to, actualPage);
                getMovimientosCuentaResponse = new GaliciaApiConnector().getMovimientosCuenta(aspNetSessionId, from, to, TipoMovimiento.TODOS, actualPage);
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
    public CommonResult getMovimientosTarjeta(Credentials credentials, String cookies) {
        GetMovimientosTarjetaResponse getMovimientosTarjetaResponse;
        try {
            log.info("[getMovimientosTarjeta] Por consultar los movimientos de la tarjeta");
            getMovimientosTarjetaResponse = new GaliciaApiConnector(credentials).getMovimientosTarjeta(cookies);
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

    @Override
    public CommonResult getCardMovements(Credentials credentials, CreditCardBrand creditCardBrand, String creditAccountNumber) {
        PostCardsMovementsResponse postCardsMovementsResponse;
        try {
            postCardsMovementsResponse = new GaliciaApiConnector(credentials).postCardsMovements(new PostCardsMovementsRequest(creditAccountNumber, creditCardBrand.name()));
        } catch (RestConnectorException e) {
            return logAndReturnError("getCardMovements", e);
        }

        if (postCardsMovementsResponse == null) {
            return CommonResult.error("postCardsMovements returns null");
        }

        DataTc data = postCardsMovementsResponse.getData().get(0);
        List<Consumption> consumptions = data.getConsumptions();
        if (!data.getPayments().isEmpty()) {
            for (Payment payment : data.getPayments()) {
                Consumption consumption = new Consumption();
                consumption.setFinalAmount(payment.getAmount());
                consumption.setTransactionAmount(payment.getAmount());
                consumption.setFinalCurrency(payment.getCurrency());
                consumption.setTransactionCurrency(payment.getCurrency());
                consumption.setTransactionDate(payment.getPaymentDate());
                consumption.setMerchantName("Pago de tarjeta " + creditCardBrand);
                consumptions.add(consumption);
            }
        }
        if (!data.getAdjustments().isEmpty()) {
            for (Adjustment adjustment : data.getAdjustments()) {
                Consumption consumption = new Consumption();
                consumption.setFinalAmount(adjustment.getTransactionAmount());
                consumption.setTransactionAmount(adjustment.getTransactionAmount());
                consumption.setFinalCurrency(adjustment.getTransactionCurrency());
                consumption.setTransactionCurrency(adjustment.getTransactionCurrency());
                consumption.setTransactionDate(adjustment.getTransactionDate());
                consumption.setSubmissionDate(adjustment.getPresentationDate());
                consumption.setBrand(adjustment.getBrand());
                consumption.setMerchantName(adjustment.getOperationDescription());
                consumption.setReceiptNumber(adjustment.getReceiptNumber());
                consumption.setMovementType(adjustment.getTransactionType());
                consumption.setAuthCode(adjustment.getAdjustmentCode());
                consumptions.add(consumption);
            }
        }

        return CommonResult.ok(data.getConsumptions());
    }

    private CommonResult logAndReturnError(String tag, RestConnectorException e) {
        boolean logError = true;
        String logMessage = "[" + tag + "] Error de Galicia";
        String msgDetail;
        if (e.getEntityError() != null && e.getEntityError() instanceof ErrorResponse) {
            ErrorResponse commonError = (ErrorResponse) e.getEntityError();
            msgDetail = commonError.getMessage();
            logMessage += " " + commonError;
        } else if (e.getStatusInfo() != null && e.getStatusInfo().equals(HttpStatus.FOUND) && e.getEntityError() != null && ((String) e.getEntityError()).contains("sesionexpirada")) {
            msgDetail = "Sesion expirada (cookie invalida)";
            logError = false;
        } else {
            msgDetail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        }
        if (logError) log.error(logMessage, e);
        return CommonResult.error("Error de Galicia [" + msgDetail + "]");
    }
}