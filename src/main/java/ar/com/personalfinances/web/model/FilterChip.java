package ar.com.personalfinances.web.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FilterChip {

    private String field;
    private String label;
    private FilterOperator operator;
    private String value;

    public String getDisplayValue() {
        if (operator == null) {
            return label + " " + value;
        }

        switch (operator) {
            case EMPTY:
            case NOT_EMPTY:
                return label + " " + operator.getLabel();
            default:
                return label + " " + operator.getSymbol() + " " + value;
        }
    }
}