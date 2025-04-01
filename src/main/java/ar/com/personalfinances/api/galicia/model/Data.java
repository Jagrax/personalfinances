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
public class Data implements Serializable {

    @JsonProperty("movements")
    private List<CreditCardMovement> movements;

    @JsonProperty("authorizations")
    private List<CreditCardMovement> authorizations;

    @JsonProperty("adjustments")
    private List<CreditCardMovement> adjustments;

    @JsonProperty("movements_manager")
    private Object movementsManager;

    @JsonProperty("total_consumptions_pesos")
    private BigDecimal totalConsumptionsPesos;

    @JsonProperty("total_consumptions_dolares")
    private BigDecimal totalConsumptionsDolares;

    @JsonProperty("error_authorizations")
    private Boolean errorAuthorizations;

    public void setTotalConsumptionsPesos(String totalConsumptionsPesos) {
        this.totalConsumptionsPesos = NumberUtils.parseBigDecimal(totalConsumptionsPesos);
    }

    public void setTotalConsumptionsDolares(String totalConsumptionsDolares) {
        this.totalConsumptionsDolares = NumberUtils.parseBigDecimal(totalConsumptionsDolares);
    }

    @Override
    public String toString() {
        return "Data [" +
                ((movements != null) ? "movements=" + Arrays.toString(movements.toArray()) + ", " : "") +
                ((authorizations != null) ? "authorizations=" + Arrays.toString(authorizations.toArray()) + ", " : "") +
                ((adjustments != null) ? "adjustments=" + Arrays.toString(adjustments.toArray()) + ", " : "") +
                ((movementsManager != null) ? "movementsManager=" + movementsManager + ", " : "") +
                ((totalConsumptionsPesos != null) ? "totalConsumptionsPesos=" + totalConsumptionsPesos + ", " : "") +
                ((totalConsumptionsDolares != null) ? "totalConsumptionsDolares=" + totalConsumptionsDolares + ", " : "") +
                ((errorAuthorizations != null) ? "errorAuthorizations=" + errorAuthorizations + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Data data = (Data) o;
        return Objects.equals(movements, data.movements) && Objects.equals(authorizations, data.authorizations) && Objects.equals(adjustments, data.adjustments) && Objects.equals(movementsManager, data.movementsManager) && Objects.equals(totalConsumptionsPesos, data.totalConsumptionsPesos) && Objects.equals(totalConsumptionsDolares, data.totalConsumptionsDolares) && Objects.equals(errorAuthorizations, data.errorAuthorizations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(movements, authorizations, adjustments, movementsManager, totalConsumptionsPesos, totalConsumptionsDolares, errorAuthorizations);
    }
}