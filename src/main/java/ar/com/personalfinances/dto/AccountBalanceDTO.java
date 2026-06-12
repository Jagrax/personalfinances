package ar.com.personalfinances.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AccountBalanceDTO {
    private Long id;
    private String name;
    private String currency;
    private BigDecimal balance;
    private String icon;
    private boolean syncEnabled;
    private String type;
    private String syncProvider;
    private Long bankId;
    private String bankName;
}
