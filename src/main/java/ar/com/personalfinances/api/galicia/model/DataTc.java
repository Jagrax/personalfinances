package ar.com.personalfinances.api.galicia.model;

import ar.com.personalfinances.util.NumberUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTc implements Serializable {

    @JsonProperty("consumptions")
    private List<Consumption> consumptions;

    @JsonProperty("adjustments")
    private List<Adjustment> adjustments;

    @JsonProperty("payments")
    private List<Payment> payments;

    @JsonProperty("authorizations")
    private List<Authorization> authorizations;

    @JsonProperty("totals")
    private Totals totals;

    @Override
    public String toString() {
        return "DataTc [" +
                ((consumptions != null) ? "consumptions=" + Arrays.toString(consumptions.toArray()) + ", " : "") +
                ((adjustments != null) ? "adjustments=" + Arrays.toString(adjustments.toArray()) + ", " : "") +
                ((payments != null) ? "payments=" + Arrays.toString(payments.toArray()) + ", " : "") +
                ((authorizations != null) ? "authorizations=" + Arrays.toString(authorizations.toArray()) + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataTc dataTc = (DataTc) o;
        return Objects.equals(consumptions, dataTc.consumptions) && Objects.equals(adjustments, dataTc.adjustments) && Objects.equals(payments, dataTc.payments) && Objects.equals(authorizations, dataTc.authorizations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumptions, adjustments, payments, authorizations);
    }
}