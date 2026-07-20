package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseMappingSearch {

    private Long userId;
    private String bankDescription;
    private String regexPattern;
    private String normalizedDescription;
    private String details;
    private Long tagId;
    private Boolean enabled;
}
