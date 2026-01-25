package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Getter
@Setter
public class BankSyncModelAttribute {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dateFrom;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dateTo;
    private String aspNetSessionId;
    private Long accountId;
    private String accountName;
}