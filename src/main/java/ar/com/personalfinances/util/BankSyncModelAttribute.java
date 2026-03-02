package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankSyncModelAttribute {

    private String aspNetSessionId;
    private Long accountId;
    private String accountName;
}