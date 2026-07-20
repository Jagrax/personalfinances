package ar.com.personalfinances.web.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class BulkExpenseUpdateRequest {

    private List<Long> expenseIds;

    private LocalDate date;

    private String description;

    private String originalDescription;

    private String details;

    private String comments;

    private List<Long> tagIds;

    private Long accountId;
}
