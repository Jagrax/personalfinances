package ar.com.personalfinances.service;

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

    CommonResult getMovimientosCuenta(String cookies, Date from, Date to);

    CommonResult getMovimientosTarjeta(String cookies);

    CommonResult getCardMovements(String bearerToken, CreditCardBrand creditCardBrand, String creditAccountNumber);
}