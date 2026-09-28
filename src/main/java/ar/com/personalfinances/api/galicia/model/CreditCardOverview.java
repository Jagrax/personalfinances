package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreditCardOverview implements Serializable {

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("brand")
    private String brand;

    @JsonProperty("settlement_closing_dates")
    private SettlementDates settlementClosingDates;

    @JsonProperty("settlement_due_dates")
    private SettlementDates settlementDueDates;

    @Override
    public String toString() {
        return "CreditCardOverview [" +
                ((accountNumber != null) ? "accountNumber='" + accountNumber + "', " : "") +
                ((brand != null) ? "brand='" + brand + "', " : "") +
                ((settlementClosingDates != null) ? "settlementClosingDates=" + settlementClosingDates + ", " : "") +
                ((settlementDueDates != null) ? "settlementDueDates=" + settlementDueDates + ", " : "") +
                "]";
    }
}
