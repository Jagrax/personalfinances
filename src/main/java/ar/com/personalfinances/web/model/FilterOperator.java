package ar.com.personalfinances.web.model;

import lombok.Getter;

@Getter
public enum FilterOperator {

    EQ("=", "es"),
    GT(">", "mayor que"),
    GTE("≥", "mayor o igual"),
    LT("<", "menor que"),
    LTE("≤", "menor o igual"),
    CONTAINS("~", "contiene"),
    EMPTY("Ø", "vacio"),
    NOT_EMPTY("!Ø", "no vacio");

    private final String symbol;
    private final String label;

    FilterOperator(String symbol, String label) {
        this.symbol = symbol;
        this.label = label;
    }
}