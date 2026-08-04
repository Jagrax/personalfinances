package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SyncResult {

    private String accountName;
    private int createdCount;
    private List<String> unmatchedDbExpenses = new ArrayList<>();

    public SyncResult(String accountName) {
        this.accountName = accountName;
    }

    public boolean hasUnmatched() {
        return !unmatchedDbExpenses.isEmpty();
    }

    public String toHtmlMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(accountName).append("</b><br>");

        if (createdCount > 0) {
            sb.append("Se ").append(createdCount > 1 ? "sincronizaron " + createdCount + " gastos" : "sincronizó 1 gasto").append("<br>");
        } else {
            sb.append("No hubo gastos nuevos para sincronizar<br>");
        }

        if (hasUnmatched()) {
            sb.append("Gastos en la DB que no se encontraron en el banco:<br>");
            sb.append("<ul>");
            for (String expense : unmatchedDbExpenses) {
                sb.append("<li>").append(expense).append("</li>");
            }
            sb.append("</ul>");
        }

        return sb.toString();
    }
}
