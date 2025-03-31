package ar.com.personalfinances.api.galicia;

import ar.com.personalfinances.util.CommonResult;

import java.util.Date;

public interface GaliciaApiManager {

    CommonResult getMovimientosCuenta(Date from, Date to);
}