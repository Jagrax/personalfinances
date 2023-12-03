package ar.com.personalfinances.entity;

import lombok.Getter;

@Getter
public enum AccountType {
    CREDIT_CARD("Credit card"),
    BANK_ACCOUNT("Bank account"),
    VIRTUAL_ACCOUNT("Virtual account"),
    GENERIC_ACCOUNT(""),
    CASH("Cash");

    private final String value;

    AccountType(String value) {
        this.value = value;
    }
}