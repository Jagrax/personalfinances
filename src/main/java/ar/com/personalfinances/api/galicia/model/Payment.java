package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Payment implements Serializable {

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("status")
    private String status;

    @JsonProperty("payment_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date paymentDate;

    @JsonProperty("payment_owner")
    private String paymentOwner;

    @Override
    public String toString() {
        return "Payment [" +
                ((currency != null) ? "currency='" + currency + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((channel != null) ? "channel='" + channel + "', " : "") +
                ((status != null) ? "status='" + status + "', " : "") +
                ((paymentDate != null) ? "paymentDate=" + paymentDate + ", " : "") +
                ((paymentOwner != null) ? "paymentOwner='" + paymentOwner + "', " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(currency, payment.currency) && Objects.equals(amount, payment.amount) && Objects.equals(channel, payment.channel) && Objects.equals(status, payment.status) && Objects.equals(paymentDate, payment.paymentDate) && Objects.equals(paymentOwner, payment.paymentOwner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, amount, channel, status, paymentDate, paymentOwner);
    }
}