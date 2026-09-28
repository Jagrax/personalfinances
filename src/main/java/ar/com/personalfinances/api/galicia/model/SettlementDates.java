package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettlementDates implements Serializable {

    @JsonProperty("previous")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate previous;

    @JsonProperty("current")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate current;

    @JsonProperty("next")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate next;

    @Override
    public String toString() {
        return "SettlementDates [" +
                ((previous != null) ? "previous=" + previous + ", " : "") +
                ((current != null) ? "current=" + current + ", " : "") +
                ((next != null) ? "next=" + next + ", " : "") +
                "]";
    }
}
