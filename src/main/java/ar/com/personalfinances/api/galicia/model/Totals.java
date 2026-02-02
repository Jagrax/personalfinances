package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Totals implements Serializable {

    @JsonProperty("total_amount_pesos")
    private BigDecimal totalAmountPesos;

    @JsonProperty("total_amount_dollars")
    private BigDecimal totalAmountDollars;

    @Override
    public String toString() {
        return "Totals [" +
                ((totalAmountPesos != null) ? "totalAmountPesos=" + totalAmountPesos + ", " : "") +
                ((totalAmountDollars != null) ? "totalAmountDollars=" + totalAmountDollars + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Totals totals = (Totals) o;
        return Objects.equals(totalAmountPesos, totals.totalAmountPesos) && Objects.equals(totalAmountDollars, totals.totalAmountDollars);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalAmountPesos, totalAmountDollars);
    }
}