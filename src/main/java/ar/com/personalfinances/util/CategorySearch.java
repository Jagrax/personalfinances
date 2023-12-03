package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.AccountType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CategorySearch {

    private String name;
    private Long ownerId;
    private List<Long> ownerIds;
}