package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopMerchantDTO {
	private String merchant;
	private BigDecimal total;
	private long count;
	private BigDecimal percent;
}
