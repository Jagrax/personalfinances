package ar.com.personalfinances.entity;

import lombok.Getter;

@Getter
public enum AccountSubtype {
    MASTER_CARD("mastercard"),
    AMEX("amex"),
    VISA("visa"),
    MERCADO_PAGO("mercadopago"),
    BANCO_GALICIA("bco_galicia"),
    BANCO_FRANCES("bco_frances"),
    BANCO_SANTANDER_RIO("bco_santander_rio"),
    BANCO_PATAGONIA("bco_patagonia"),
    BANCO_PROVINCIA("bco_provincia");

    private final String icon;

    AccountSubtype(String icon) {
        this.icon = icon;
    }
}