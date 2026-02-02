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
public class Adjustment implements Serializable {

    @JsonProperty("status")
    private String status;
    
    @JsonProperty("transaction_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date transactionDate;
    
    @JsonProperty("presentation_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date presentationDate;
    
    @JsonProperty("adjustment_code")
    private String adjustmentCode;
    
    @JsonProperty("adjustment_code_internal")
    private String adjustmentCodeInternal;
    
    @JsonProperty("operation_channel")
    private String operationChannel;
    
    @JsonProperty("operation_description")
    private String operationDescription;
    
    @JsonProperty("operation_description_internal")
    private String operationDescriptionInternal;
    
    @JsonProperty("transaction_type")
    private String transactionType;
    
    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;
    
    @JsonProperty("receipt_number")
    private String receiptNumber;
    
    @JsonProperty("transaction_currency")
    private String transactionCurrency;
    
    @JsonProperty("brand")
    private String brand;

    @Override
    public String toString() {
        return "Adjustment [" +
                ((status != null) ? "status='" + status + "', " : "") +
                ((transactionDate != null) ? "transactionDate=" + transactionDate + ", " : "") +
                ((presentationDate != null) ? "presentationDate=" + presentationDate + ", " : "") +
                ((adjustmentCode != null) ? "adjustmentCode='" + adjustmentCode + "', " : "") +
                ((adjustmentCodeInternal != null) ? "adjustmentCodeInternal='" + adjustmentCodeInternal + "', " : "") +
                ((operationChannel != null) ? "operationChannel='" + operationChannel + "', " : "") +
                ((operationDescription != null) ? "operationDescription='" + operationDescription + "', " : "") +
                ((operationDescriptionInternal != null) ? "operationDescriptionInternal='" + operationDescriptionInternal + "', " : "") +
                ((transactionType != null) ? "transactionType='" + transactionType + "', " : "") +
                ((transactionAmount != null) ? "transactionAmount=" + transactionAmount + ", " : "") +
                ((receiptNumber != null) ? "receiptNumber='" + receiptNumber + "', " : "") +
                ((transactionCurrency != null) ? "transactionCurrency='" + transactionCurrency + "', " : "") +
                ((brand != null) ? "brand='" + brand + "', " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Adjustment that = (Adjustment) o;
        return Objects.equals(status, that.status) && Objects.equals(transactionDate, that.transactionDate) && Objects.equals(presentationDate, that.presentationDate) && Objects.equals(adjustmentCode, that.adjustmentCode) && Objects.equals(adjustmentCodeInternal, that.adjustmentCodeInternal) && Objects.equals(operationChannel, that.operationChannel) && Objects.equals(operationDescription, that.operationDescription) && Objects.equals(operationDescriptionInternal, that.operationDescriptionInternal) && Objects.equals(transactionType, that.transactionType) && Objects.equals(transactionAmount, that.transactionAmount) && Objects.equals(receiptNumber, that.receiptNumber) && Objects.equals(transactionCurrency, that.transactionCurrency) && Objects.equals(brand, that.brand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, transactionDate, presentationDate, adjustmentCode, adjustmentCodeInternal, operationChannel, operationDescription, operationDescriptionInternal, transactionType, transactionAmount, receiptNumber, transactionCurrency, brand);
    }
}