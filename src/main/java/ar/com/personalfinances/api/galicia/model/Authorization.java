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
public class Authorization implements Serializable {

    @JsonProperty("transaction_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date transactionDate;
    
    @JsonProperty("merchant_number")
    private String merchantNumber;
    
    @JsonProperty("merchant_name")
    private String merchantName;
    
    @JsonProperty("market")
    private String market;
    
    @JsonProperty("installment_plan")
    private Long installmentPlan;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("transaction_currency")
    private String transactionCurrency;
    
    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("transaction_status")
    private String transactionStatus;
    
    @JsonProperty("transaction_description")
    private String transactionDescription;
    
    @JsonProperty("bin")
    private String bin;
    
    @JsonProperty("last_four_digits")
    private String lastFourDigits;
    
    @JsonProperty("brand")
    private String brand;
    
    @JsonProperty("channel")
    private String channel;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("acquirer_code")
    private String acquirerCode;
    
    @JsonProperty("acquirer_description")
    private String acquirerDescription;
    
    @JsonProperty("merchant_country")
    private String merchantCountry;
    
    @JsonProperty("merchant_city")
    private String merchantCity;
    
    @JsonProperty("credit_account_number")
    private String creditAccountNumber;
    
    @JsonProperty("authorization_code")
    private String authorizationCode;
    
    @JsonProperty("rejected_motive")
    private String rejectedMotive;
    
    @JsonProperty("date_front_format")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "GMT-03:00")
    private Date dateFrontFormat;

    @Override
    public String toString() {
        return "Authorization [" +
                ((transactionDate != null) ? "transactionDate=" + transactionDate + ", " : "") +
                ((merchantNumber != null) ? "merchantNumber='" + merchantNumber + "', " : "") +
                ((merchantName != null) ? "merchantName='" + merchantName + "', " : "") +
                ((market != null) ? "market='" + market + "', " : "") +
                ((installmentPlan != null) ? "installmentPlan=" + installmentPlan + ", " : "") +
                ((currency != null) ? "currency='" + currency + "', " : "") +
                ((transactionCurrency != null) ? "transactionCurrency='" + transactionCurrency + "', " : "") +
                ((transactionAmount != null) ? "transactionAmount=" + transactionAmount + ", " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((transactionStatus != null) ? "transactionStatus='" + transactionStatus + "', " : "") +
                ((transactionDescription != null) ? "transactionDescription='" + transactionDescription + "', " : "") +
                ((bin != null) ? "bin='" + bin + "', " : "") +
                ((lastFourDigits != null) ? "lastFourDigits='" + lastFourDigits + "', " : "") +
                ((brand != null) ? "brand='" + brand + "', " : "") +
                ((channel != null) ? "channel='" + channel + "', " : "") +
                ((type != null) ? "type='" + type + "', " : "") +
                ((acquirerCode != null) ? "acquirerCode='" + acquirerCode + "', " : "") +
                ((acquirerDescription != null) ? "acquirerDescription='" + acquirerDescription + "', " : "") +
                ((merchantCountry != null) ? "merchantCountry='" + merchantCountry + "', " : "") +
                ((merchantCity != null) ? "merchantCity='" + merchantCity + "', " : "") +
                ((creditAccountNumber != null) ? "creditAccountNumber='" + creditAccountNumber + "', " : "") +
                ((authorizationCode != null) ? "authorizationCode='" + authorizationCode + "', " : "") +
                ((rejectedMotive != null) ? "rejectedMotive='" + rejectedMotive + "', " : "") +
                ((dateFrontFormat != null) ? "dateFrontFormat=" + dateFrontFormat + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Authorization that = (Authorization) o;
        return Objects.equals(transactionDate, that.transactionDate) && Objects.equals(merchantNumber, that.merchantNumber) && Objects.equals(merchantName, that.merchantName) && Objects.equals(market, that.market) && Objects.equals(installmentPlan, that.installmentPlan) && Objects.equals(currency, that.currency) && Objects.equals(transactionCurrency, that.transactionCurrency) && Objects.equals(transactionAmount, that.transactionAmount) && Objects.equals(amount, that.amount) && Objects.equals(transactionStatus, that.transactionStatus) && Objects.equals(transactionDescription, that.transactionDescription) && Objects.equals(bin, that.bin) && Objects.equals(lastFourDigits, that.lastFourDigits) && Objects.equals(brand, that.brand) && Objects.equals(channel, that.channel) && Objects.equals(type, that.type) && Objects.equals(acquirerCode, that.acquirerCode) && Objects.equals(acquirerDescription, that.acquirerDescription) && Objects.equals(merchantCountry, that.merchantCountry) && Objects.equals(merchantCity, that.merchantCity) && Objects.equals(creditAccountNumber, that.creditAccountNumber) && Objects.equals(authorizationCode, that.authorizationCode) && Objects.equals(rejectedMotive, that.rejectedMotive) && Objects.equals(dateFrontFormat, that.dateFrontFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionDate, merchantNumber, merchantName, market, installmentPlan, currency, transactionCurrency, transactionAmount, amount, transactionStatus, transactionDescription, bin, lastFourDigits, brand, channel, type, acquirerCode, acquirerDescription, merchantCountry, merchantCity, creditAccountNumber, authorizationCode, rejectedMotive, dateFrontFormat);
    }
}