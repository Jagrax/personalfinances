package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDetailDTO {
	private Long id;
	private String date;
	private String description;
	private String details;
	private String installment;
	private String tags;
	private BigDecimal amount;
}
