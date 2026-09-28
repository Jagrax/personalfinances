package ar.com.personalfinances.api.galicia.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
public class CardSettlements implements Serializable {

    private LocalDate previous;
    private LocalDate current;
    private LocalDate next;
    private LocalDate previousDue;
    private LocalDate currentDue;
    private LocalDate nextDue;

    public static CardSettlements from(SettlementDates closingDates, SettlementDates dueDates) {
        CardSettlements settlements = new CardSettlements();
        if (closingDates != null) {
            settlements.setPrevious(closingDates.getPrevious());
            settlements.setCurrent(closingDates.getCurrent());
            settlements.setNext(closingDates.getNext());
        }
        if (dueDates != null) {
            settlements.setPreviousDue(dueDates.getPrevious());
            settlements.setCurrentDue(dueDates.getCurrent());
            settlements.setNextDue(dueDates.getNext());
        }
        return settlements;
    }

    @Override
    public String toString() {
        return "CardSettlements [" +
                ((previous != null) ? "previous=" + previous + ", " : "") +
                ((current != null) ? "current=" + current + ", " : "") +
                ((next != null) ? "next=" + next + ", " : "") +
                ((previousDue != null) ? "previousDue=" + previousDue + ", " : "") +
                ((currentDue != null) ? "currentDue=" + currentDue + ", " : "") +
                ((nextDue != null) ? "nextDue=" + nextDue + ", " : "") +
                "]";
    }
}