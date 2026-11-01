package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountExpenseSegmentDTO {
	private Long accountId;
	private String accountName;
	private String currency;
	private BigDecimal total;
	private String label;
	private String icon;
	private Long bankId;
	private LocalDateTime lastSyncAt;
}
