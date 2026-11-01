package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyExpenseDTO {
	private String name;
	private String type;
	private BigDecimal average;
	private long months;
}
