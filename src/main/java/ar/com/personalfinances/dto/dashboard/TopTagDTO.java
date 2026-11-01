package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopTagDTO {
	private Long tagId;
	private String tagName;
	private String tagColor;
	private BigDecimal total;
	private BigDecimal percent;
}
