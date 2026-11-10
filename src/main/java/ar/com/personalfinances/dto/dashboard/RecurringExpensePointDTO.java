package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpensePointDTO {
	private int year;
	private int month;
	private String label;
	private Map<String, BigDecimal> totalsByConcept = new LinkedHashMap<>();
	private BigDecimal total;
}
