package ar.com.personalfinances.dto.dashboard;

import ar.com.personalfinances.util.SyncResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankSyncSummaryDTO {

    private int createdCount;
    private List<SyncResult> results = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public boolean isError() {
        return results.isEmpty() && !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !errors.isEmpty() || results.stream().anyMatch(SyncResult::hasUnmatched);
    }

    public String getSummaryText() {
        if (isError()) {
            return "No se pudo completar la sincronización en " + errors.size() + " cuenta" + (errors.size() > 1 ? "s" : "") + ".";
        }

        String text;
        if (createdCount == 0) {
            text = "No hubo gastos nuevos";
        } else {
            String verb = createdCount == 1 ? "Se sincronizó 1 gasto" : "Se sincronizaron " + createdCount + " gastos";
            List<SyncResult> withNewExpenses = results.stream()
                    .filter(r -> r.getCreatedCount() > 0)
                    .collect(Collectors.toList());
            if (withNewExpenses.size() == 1) {
                text = verb + " en " + withNewExpenses.get(0).getAccountName();
            } else {
                text = verb + " en " + withNewExpenses.stream()
                        .map(r -> r.getAccountName() + " (" + r.getCreatedCount() + ")")
                        .collect(Collectors.joining(", "));
            }
        }

        if (hasWarnings()) {
            text += ", con advertencias";
        }
        return text + ".";
    }
}
