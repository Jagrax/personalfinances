package ar.com.personalfinances.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanTicketResponse {
    private BigDecimal total;
    private List<ScanItemDTO> items;
}