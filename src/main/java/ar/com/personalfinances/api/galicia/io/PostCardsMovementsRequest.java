package ar.com.personalfinances.api.galicia.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostCardsMovementsRequest implements Serializable {

    @JsonProperty("credit_account_number")// "769200529", //769200529|1328457
    private String creditAccountNumber;

    @JsonProperty("brand")// "VISA", //VISA|MASTER
    private String brand;

    @JsonProperty("date_from")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date dateFrom;

    @JsonProperty("date_to")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date dateTo;

    public PostCardsMovementsRequest() {
    }

    public PostCardsMovementsRequest(String creditAccountNumber, String brand) {
        this.creditAccountNumber = creditAccountNumber;
        this.brand = brand;
    }

    @Override
    public String toString() {
        return "PostCardsMovementsRequest [" +
                ((creditAccountNumber != null) ? "creditAccountNumber='" + creditAccountNumber + "', " : "") +
                ((brand != null) ? "brand='" + brand + "', " : "") +
                ((dateFrom != null) ? "dateFrom=" + dateFrom + ", " : "") +
                ((dateTo != null) ? "dateTo=" + dateTo + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostCardsMovementsRequest that = (PostCardsMovementsRequest) o;
        return Objects.equals(creditAccountNumber, that.creditAccountNumber) && Objects.equals(brand, that.brand) && Objects.equals(dateFrom, that.dateFrom) && Objects.equals(dateTo, that.dateTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creditAccountNumber, brand, dateFrom, dateTo);
    }
}