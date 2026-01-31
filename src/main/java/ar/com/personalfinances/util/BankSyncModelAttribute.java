package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Getter
@Setter
public class BankSyncModelAttribute {

    private String aspNetSessionId;
    private Long accountId;
    private String accountName;
}