package ar.com.personalfinances.exception;

import ar.com.personalfinances.web.model.FilterOperator;
import lombok.Getter;

@Getter
public class InvalidSearchFilterException extends RuntimeException {

    private final String fieldName;
    private final FilterOperator operator;
    private final Object value;

    public InvalidSearchFilterException(String fieldName, FilterOperator operator, Object value) {
        super(String.format("Invalid filter for field [%s] with operator [%s] and value [%s]", fieldName, operator, value));

        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }
}