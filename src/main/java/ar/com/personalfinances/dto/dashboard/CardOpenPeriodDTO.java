package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardOpenPeriodDTO {
	private Long accountId;
	private String accountName;
	private BigDecimal total;
	private LocalDate closingDate;
	private LocalDate dueDate;
	private String icon;
}
