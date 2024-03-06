package ar.com.personalfinances.entity;

import lombok.Getter;

@Getter
public enum AccountType {
    BANK_ACCOUNT("Bank account", 0),
    CREDIT_CARD("Credit card", 1),
    VIRTUAL_ACCOUNT("Virtual account", 2),
    CASH("Cash", 3),
    GENERIC_ACCOUNT("", 9);

    private final String value;
    private final int order;

    AccountType(String value, int order) {
        this.value = value;
        this.order = order;
    }
}