package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Consumption implements Serializable {

    @JsonProperty("last_four_digits")
    private String lastFourDigits;
    
    @JsonProperty("receipt_number")
    private String receiptNumber;

    @JsonProperty("transaction_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;
    
    @JsonProperty("auth_code")
    private String authCode;
    
    @JsonProperty("merchant_name")
    private String merchantName;
    
    @JsonProperty("installment_plan")
    private Long installmentPlan;
    
    @JsonProperty("installment_number")
    private Long installmentNumber;
    
    @JsonProperty("transaction_currency")
    private String transactionCurrency;
    
    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;
    
    @JsonProperty("submission_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate submissionDate;
    
    @JsonProperty("final_currency")
    private String finalCurrency;
    
    @JsonProperty("movement_type")
    private String movementType;
    
    @JsonProperty("brand")
    private String brand;
    
    @JsonProperty("final_amount")
    private BigDecimal finalAmount;
    
    @JsonProperty("operation_type")
    private String operationType;
    
    @JsonProperty("credit_account")
    private String creditAccount;

    @Override
    public String toString() {
        return "Consumption [" +
                ((lastFourDigits != null) ? "lastFourDigits='" + lastFourDigits + "', " : "") +
                ((receiptNumber != null) ? "receiptNumber='" + receiptNumber + "', " : "") +
                ((transactionDate != null) ? "transactionDate=" + transactionDate + ", " : "") +
                ((authCode != null) ? "authCode='" + authCode + "', " : "") +
                ((merchantName != null) ? "merchantName='" + merchantName + "', " : "") +
                ((installmentPlan != null) ? "installmentPlan=" + installmentPlan + ", " : "") +
                ((installmentNumber != null) ? "installmentNumber=" + installmentNumber + ", " : "") +
                ((transactionCurrency != null) ? "transactionCurrency='" + transactionCurrency + "', " : "") +
                ((transactionAmount != null) ? "transactionAmount=" + transactionAmount + ", " : "") +
                ((submissionDate != null) ? "submissionDate=" + submissionDate + ", " : "") +
                ((finalCurrency != null) ? "finalCurrency='" + finalCurrency + "', " : "") +
                ((movementType != null) ? "movementType='" + movementType + "', " : "") +
                ((brand != null) ? "brand='" + brand + "', " : "") +
                ((finalAmount != null) ? "finalAmount=" + finalAmount + ", " : "") +
                ((operationType != null) ? "operationType='" + operationType + "', " : "") +
                ((creditAccount != null) ? "creditAccount='" + creditAccount + "', " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Consumption that = (Consumption) o;
        return Objects.equals(lastFourDigits, that.lastFourDigits) && Objects.equals(receiptNumber, that.receiptNumber) && Objects.equals(transactionDate, that.transactionDate) && Objects.equals(authCode, that.authCode) && Objects.equals(merchantName, that.merchantName) && Objects.equals(installmentPlan, that.installmentPlan) && Objects.equals(installmentNumber, that.installmentNumber) && Objects.equals(transactionCurrency, that.transactionCurrency) && Objects.equals(transactionAmount, that.transactionAmount) && Objects.equals(submissionDate, that.submissionDate) && Objects.equals(finalCurrency, that.finalCurrency) && Objects.equals(movementType, that.movementType) && Objects.equals(brand, that.brand) && Objects.equals(finalAmount, that.finalAmount) && Objects.equals(operationType, that.operationType) && Objects.equals(creditAccount, that.creditAccount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastFourDigits, receiptNumber, transactionDate, authCode, merchantName, installmentPlan, installmentNumber, transactionCurrency, transactionAmount, submissionDate, finalCurrency, movementType, brand, finalAmount, operationType, creditAccount);
    }
}