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
public class Consumption implements Serializable {

    @JsonProperty("last_four_digits")// "8996",
    private String lastFourDigits;
    
    @JsonProperty("receipt_number")// "00000447",
    private String receiptNumber;
    
    @JsonProperty("transaction_date")// "2025-05-13",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date transactionDate;
    
    @JsonProperty("auth_code")// "00005049",
    private String authCode;
    
    @JsonProperty("merchant_name")// "MARKETPLACE SAMSUNG",
    private String merchantName;
    
    @JsonProperty("installment_plan")// 18,
    private Long installmentPlan;
    
    @JsonProperty("installment_number")// 9,
    private Long installmentNumber;
    
    @JsonProperty("transaction_currency")// "ARS",
    private String transactionCurrency;
    
    @JsonProperty("transaction_amount")// 1499999.76,
    private BigDecimal transactionAmount;
    
    @JsonProperty("submission_date")// "2025-11-28",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date submissionDate;
    
    @JsonProperty("final_currency")// "ARS",
    private String finalCurrency;
    
    @JsonProperty("movement_type")// "instalments",
    private String movementType;
    
    @JsonProperty("brand")// "VISA",
    private String brand;
    
    @JsonProperty("final_amount")// 83333.32,
    private BigDecimal finalAmount;
    
    @JsonProperty("operation_type")// "733",
    private String operationType;
    
    @JsonProperty("credit_account")// "769200529"
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