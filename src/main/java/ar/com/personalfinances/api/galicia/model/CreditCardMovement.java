package ar.com.personalfinances.api.galicia.model;

import ar.com.personalfinances.util.NumberUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreditCardMovement implements Serializable {

    @JsonProperty("index")
    private Long index;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")
    private Long currency;
    
    @JsonProperty("currency_symbol")
    private String currencySymbol;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime date;
    
    @JsonProperty("movement_description")
    private String movementDescription;
    
    @JsonProperty("total_installment")
    private Integer totalInstallment;
    
    @JsonProperty("current_installment")
    private Integer currentInstallment;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("price")
    private String price;
    
    @JsonProperty("converted_amount")
    private String convertedAmount;
    
    @JsonProperty("payment_channel")
    private Long paymentChannel;
    
    @JsonProperty("id_mov")
    private Long idMov;
    
    @JsonProperty("pending")
    private Boolean pending;
    
    @JsonProperty("next_bussines_day")
    private String nextBussinesDay;
    
    @JsonProperty("date_format_hour")
    private String dateFormatHour;
    
    @JsonProperty("motive")
    private String motive;
    
    @JsonProperty("reject_flag")
    private Boolean rejectFlag;
    
    @JsonProperty("is_operation_managment")
    private Boolean isOperationManagment;
    
    @JsonProperty("last_digits_owner")
    private String lastDigitsOwner;

    public void setAmount(String amount) {
        this.amount = NumberUtils.parseBigDecimal(amount);
    }

    @Override
    public String toString() {
        return "CreditCardMovement [" +
                ((index != null) ? "index=" + index + ", " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((currency != null) ? "currency=" + currency + ", " : "") +
                ((currencySymbol != null) ? "currencySymbol='" + currencySymbol + "', " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((date != null) ? "date=" + date + ", " : "") +
                ((movementDescription != null) ? "movementDescription='" + movementDescription + "', " : "") +
                ((totalInstallment != null) ? "totalInstallment=" + totalInstallment + ", " : "") +
                ((currentInstallment != null) ? "currentInstallment=" + currentInstallment + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((price != null) ? "price='" + price + "', " : "") +
                ((convertedAmount != null) ? "convertedAmount='" + convertedAmount + "', " : "") +
                ((paymentChannel != null) ? "paymentChannel=" + paymentChannel + ", " : "") +
                ((idMov != null) ? "idMov=" + idMov + ", " : "") +
                ((pending != null) ? "pending=" + pending + ", " : "") +
                ((nextBussinesDay != null) ? "nextBussinesDay='" + nextBussinesDay + "', " : "") +
                ((dateFormatHour != null) ? "dateFormatHour='" + dateFormatHour + "', " : "") +
                ((motive != null) ? "motive='" + motive + "', " : "") +
                ((rejectFlag != null) ? "rejectFlag=" + rejectFlag + ", " : "") +
                ((isOperationManagment != null) ? "isOperationManagment=" + isOperationManagment + ", " : "") +
                ((lastDigitsOwner != null) ? "lastDigitsOwner='" + lastDigitsOwner + "', " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreditCardMovement that = (CreditCardMovement) o;
        return Objects.equals(index, that.index) && Objects.equals(amount, that.amount) && Objects.equals(currency, that.currency) && Objects.equals(currencySymbol, that.currencySymbol) && Objects.equals(description, that.description) && Objects.equals(date, that.date) && Objects.equals(movementDescription, that.movementDescription) && Objects.equals(totalInstallment, that.totalInstallment) && Objects.equals(currentInstallment, that.currentInstallment) && Objects.equals(name, that.name) && Objects.equals(price, that.price) && Objects.equals(convertedAmount, that.convertedAmount) && Objects.equals(paymentChannel, that.paymentChannel) && Objects.equals(idMov, that.idMov) && Objects.equals(pending, that.pending) && Objects.equals(nextBussinesDay, that.nextBussinesDay) && Objects.equals(dateFormatHour, that.dateFormatHour) && Objects.equals(motive, that.motive) && Objects.equals(rejectFlag, that.rejectFlag) && Objects.equals(isOperationManagment, that.isOperationManagment) && Objects.equals(lastDigitsOwner, that.lastDigitsOwner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, amount, currency, currencySymbol, description, date, movementDescription, totalInstallment, currentInstallment, name, price, convertedAmount, paymentChannel, idMov, pending, nextBussinesDay, dateFormatHour, motive, rejectFlag, isOperationManagment, lastDigitsOwner);
    }
}