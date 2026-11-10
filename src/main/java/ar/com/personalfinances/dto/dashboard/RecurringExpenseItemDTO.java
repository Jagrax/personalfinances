package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpenseItemDTO {
	private String date;
	private BigDecimal amount;
}
