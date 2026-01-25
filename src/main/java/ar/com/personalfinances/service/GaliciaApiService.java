package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.util.Credentials;
import ar.com.personalfinances.util.CommonResult;
import lombok.Getter;

import java.util.Date;

public interface GaliciaApiService {

    @Getter
    enum TipoMovimiento {
        TODOS("Todos"),
        INGRESOS("Ingresos"),
        EGRESOS("Egresos");

        private final String value;

        TipoMovimiento(String value) {
            this.value = value;
        }
    }

    enum CreditCardBrand {
        VISA,
        MASTER,
    }

    CommonResult getMovimientosCuenta(String aspNetSessionId, Date from, Date to);

    CommonResult getMovimientosTarjeta(Credentials credentials, String cookies);

    CommonResult getCardMovements(Credentials credentials, CreditCardBrand creditCardBrand, String creditAccountNumber);
}