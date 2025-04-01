package ar.com.personalfinances.api.galicia.model;

import ar.com.personalfinances.util.NumberUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreditCardMovement implements Serializable {

    @JsonProperty("index")//: 0,
    private Long index;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")//: 1,
    private Long currency;
    
    @JsonProperty("currency_symbol")//: "ARS",
    private String currencySymbol;
    
    @JsonProperty("description")//: "MERPAGO*COTO",
    private String description;
    
    @JsonProperty("date")//: "2025-03-30T00:00:00",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT-03:00")
    private Date date;
    
    @JsonProperty("movement_description")//: "MERPAGO*COTO",
    private String movementDescription;
    
    @JsonProperty("total_installment")//: "0",
    private String totalInstallment;
    
    @JsonProperty("current_installment")//: "",
    private String currentInstallment;
    
    @JsonProperty("name")//: "Titular",
    private String name;
    
    @JsonProperty("price")//: null,
    private String price;
    
    @JsonProperty("converted_amount")//: null,
    private String convertedAmount;
    
    @JsonProperty("payment_channel")//: 0,
    private Long paymentChannel;
    
    @JsonProperty("id_mov")//: 0,
    private Long idMov;
    
    @JsonProperty("pending")//: false,
    private Boolean pending;
    
    @JsonProperty("next_bussines_day")//: null,
    private String nextBussinesDay;
    
    @JsonProperty("date_format_hour")//: "30/03/2025 a las 14:06 h",
    private String dateFormatHour;
    
    @JsonProperty("motive")//: "",
    private String motive;
    
    @JsonProperty("reject_flag")//: false,
    private Boolean rejectFlag;
    
    @JsonProperty("is_operation_managment")//: false,
    private Boolean isOperationManagment;
    
    @JsonProperty("last_digits_owner")//: "3628"
    private String lastDigitsOwner;

    public Long getIndex() {
        return index;
    }

    public void setIndex(Long index) {
        this.index = index;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setAmount(String amount) {
        this.amount = NumberUtils.parseBigDecimal(amount);
    }

    public Long getCurrency() {
        return currency;
    }

    public void setCurrency(Long currency) {
        this.currency = currency;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getMovementDescription() {
        return movementDescription;
    }

    public void setMovementDescription(String movementDescription) {
        this.movementDescription = movementDescription;
    }

    public String getTotalInstallment() {
        return totalInstallment;
    }

    public void setTotalInstallment(String totalInstallment) {
        this.totalInstallment = totalInstallment;
    }

    public String getCurrentInstallment() {
        return currentInstallment;
    }

    public void setCurrentInstallment(String currentInstallment) {
        this.currentInstallment = currentInstallment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getConvertedAmount() {
        return convertedAmount;
    }

    public void setConvertedAmount(String convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    public Long getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(Long paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public Long getIdMov() {
        return idMov;
    }

    public void setIdMov(Long idMov) {
        this.idMov = idMov;
    }

    public Boolean getPending() {
        return pending;
    }

    public void setPending(Boolean pending) {
        this.pending = pending;
    }

    public String getNextBussinesDay() {
        return nextBussinesDay;
    }

    public void setNextBussinesDay(String nextBussinesDay) {
        this.nextBussinesDay = nextBussinesDay;
    }

    public String getDateFormatHour() {
        return dateFormatHour;
    }

    public void setDateFormatHour(String dateFormatHour) {
        this.dateFormatHour = dateFormatHour;
    }

    public String getMotive() {
        return motive;
    }

    public void setMotive(String motive) {
        this.motive = motive;
    }

    public Boolean getRejectFlag() {
        return rejectFlag;
    }

    public void setRejectFlag(Boolean rejectFlag) {
        this.rejectFlag = rejectFlag;
    }

    public Boolean getOperationManagment() {
        return isOperationManagment;
    }

    public void setOperationManagment(Boolean operationManagment) {
        isOperationManagment = operationManagment;
    }

    public String getLastDigitsOwner() {
        return lastDigitsOwner;
    }

    public void setLastDigitsOwner(String lastDigitsOwner) {
        this.lastDigitsOwner = lastDigitsOwner;
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
                ((totalInstallment != null) ? "totalInstallment='" + totalInstallment + "', " : "") +
                ((currentInstallment != null) ? "currentInstallment='" + currentInstallment + "', " : "") +
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