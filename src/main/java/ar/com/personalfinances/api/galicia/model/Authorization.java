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

    @JsonProperty("transaction_date")// "2026-01-20",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT-03:00")
    private Date transactionDate;
    
    @JsonProperty("merchant_number")// "73109449",
    private String merchantNumber;
    
    @JsonProperty("merchant_name")// "PVS*SUPER CNEL APOLINA",
    private String merchantName;
    
    @JsonProperty("market")// "5411",
    private String market;
    
    @JsonProperty("installment_plan")// 1,
    private Long installmentPlan;
    
    @JsonProperty("currency")// "ARS",
    private String currency;
    
    @JsonProperty("transaction_currency")// "ARS",
    private String transactionCurrency;
    
    @JsonProperty("transaction_amount")// "15830.00",
    private BigDecimal transactionAmount;
    
    @JsonProperty("amount")// "15830.00",
    private BigDecimal amount;
    
    @JsonProperty("transaction_status")// "APPROVED",
    private String transactionStatus;
    
    @JsonProperty("transaction_description")// "APPROVED WITHOUT BALANCES",
    private String transactionDescription;
    
    @JsonProperty("bin")// "550568",
    private String bin;
    
    @JsonProperty("last_four_digits")// "3628",
    private String lastFourDigits;
    
    @JsonProperty("brand")// "MASTER",
    private String brand;
    
    @JsonProperty("channel")// "NFC",
    private String channel;
    
    @JsonProperty("type")// "PURCHASE",
    private String type;
    
    @JsonProperty("acquirer_code")// "PRISMA",
    private String acquirerCode;
    
    @JsonProperty("acquirer_description")// "PRISMA",
    private String acquirerDescription;
    
    @JsonProperty("merchant_country")// "AR",
    private String merchantCountry;
    
    @JsonProperty("merchant_city")// "VILLA CRESPO",
    private String merchantCity;
    
    @JsonProperty("credit_account_number")// null,
    private String creditAccountNumber;
    
    @JsonProperty("authorization_code")// null,
    private String authorizationCode;
    
    @JsonProperty("rejected_motive")// "",
    private String rejectedMotive;
    
    @JsonProperty("date_front_format")// "2026-01-20 19:38"
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