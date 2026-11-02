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

        // El banco manda formato argentino (1.234,56), pero se aceptan ambos ordenes de separadores
        importe = importe.trim();

        if (importe.contains(".") && importe.indexOf('.') < importe.lastIndexOf(',')) {
            importe = importe.replace(".", "");
        }

        if (importe.contains(",")) {
            if (importe.contains(".")) {
                importe = importe.replace(".", "").replace(",", ".");
            } else {
                importe = importe.replace(",", ".");
            }
        }

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
