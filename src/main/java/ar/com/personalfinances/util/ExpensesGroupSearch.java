package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class ExpensesGroupSearch {

    private String name;
    private String description;
    private Long userId;
    private List<Long> userIds;
    private Long creationUserId;
}