package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataOverview implements Serializable {

    @JsonProperty("credit_cards")
    private List<CreditCardOverview> creditCards;

    @Override
    public String toString() {
        return "DataOverview [" +
                ((creditCards != null) ? "creditCards=" + creditCards + ", " : "") +
                "]";
    }
}
