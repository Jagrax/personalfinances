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
}