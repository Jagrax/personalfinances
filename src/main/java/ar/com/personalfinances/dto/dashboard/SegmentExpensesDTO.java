package ar.com.personalfinances.dto.dashboard;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SegmentExpensesDTO {
	private boolean creditCard;
	private List<ExpenseDetailDTO> expenses = new ArrayList<>();
}
