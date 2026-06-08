package ar.com.personalfinances.service;

import ar.com.personalfinances.util.CommonResult;
import lombok.Getter;

import java.time.LocalDate;

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

    CommonResult getMovimientosCuenta(String cookies, LocalDate from, LocalDate to);

    CommonResult getCardMovements(String cookies, CreditCardBrand creditCardBrand, String creditAccountNumber);

    CommonResult getCardsOverview(String cookies);

    CommonResult establishCuentasSession(String onlinebankingCookies);

    CommonResult getSeccionMisCuentas(String onlinebankingCookies);
}
