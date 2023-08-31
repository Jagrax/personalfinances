package ar.com.personalfinances.entity;

import lombok.Getter;

@Getter
public enum AccountSubtype {
    // Credit cards
    MASTER_CARD("mastercard"),
    AMEX("amex"),
    VISA("visa"),
    // Virtual accounts
    MERCADO_PAGO("mercadopago"),
    // Bank accounts
    BANCO_GALICIA("bco_galicia"),
    BANCO_FRANCES("bco_frances"),
    BANCO_SANTANDER_RIO("bco_santander_rio"),
    BANCO_PATAGONIA("bco_patagonia");

    private final String icon;

    AccountSubtype(String icon) {
        this.icon = icon;
    }
}