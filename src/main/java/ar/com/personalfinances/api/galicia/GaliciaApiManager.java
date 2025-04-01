package ar.com.personalfinances.api.galicia;

import ar.com.personalfinances.util.CommonResult;
import lombok.Getter;

import java.util.Date;

public interface GaliciaApiManager {

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

    CommonResult getMovimientosCuenta(String cookies, Date from, Date to);

    CommonResult getMovimientosTarjeta(String cookies);
}