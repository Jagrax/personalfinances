package ar.com.personalfinances.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumberUtils {

    public static double CASI_CERO = 1E-6;
    public static final int BIGDECIMAL_SCALE = 2;
    public static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(BIGDECIMAL_SCALE, RoundingMode.HALF_UP);

    public static BigDecimal parseBigDecimal(String importe) {
        if (importe == null || importe.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Eliminar posibles espacios antes y después
        importe = importe.trim();

        // Paso 1: Eliminar separadores de miles
        if (importe.contains(".") && importe.indexOf('.') < importe.lastIndexOf(',')) {
            // Si tiene punto antes de la coma, entonces es un separador de miles, lo eliminamos
            importe = importe.replace(".", "");
        }

        // Paso 2: Detectar formato de coma y convertir a punto
        if (importe.contains(",")) {
            // Si el número contiene coma, lo tratamos como separador decimal (formato español)
            if (importe.contains(".")) {
                // Si tiene punto y coma, reemplazamos el punto (miles) y la coma (decimal) por punto
                importe = importe.replace(".", "").replace(",", ".");
            } else {
                // Si tiene solo coma, significa que es el separador decimal
                importe = importe.replace(",", ".");
            }
        }

        // Paso 3: Si no tiene coma ni punto, es un número entero sin decimales, lo dejamos tal cual
        return new BigDecimal(importe);
    }

    public static boolean isZero(BigDecimal a) {
        return bigDecimalIgual(a, ZERO_SCALED);
    }

    public static boolean bigDecimalIgual(BigDecimal a, BigDecimal b) {
        return bigDecimalIgual(a, b, CASI_CERO);
    }

    public static boolean bigDecimalIgual(BigDecimal a, BigDecimal b, double tolerance) {
        return (a.subtract(b)).abs().doubleValue() <= tolerance;
    }
}
