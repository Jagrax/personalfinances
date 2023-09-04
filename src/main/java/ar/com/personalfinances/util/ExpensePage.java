package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.Expense;
import lombok.Getter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
public class ExpensePage extends PageImpl<Expense> {

    private final Map<String, BigDecimal> totalAmountsAcumByCurrency;
    private final Map<String, BigDecimal> onPageTotalAmountsAcumByCurrency;

    public ExpensePage(List<Expense> content, Pageable pageable, long total, Map<String, BigDecimal> totalAmountsAcumByCurrency, Map<String, BigDecimal> onPageTotalAmountsAcumByCurrency) {
        super(content, pageable, total);
        this.onPageTotalAmountsAcumByCurrency = onPageTotalAmountsAcumByCurrency;
        this.totalAmountsAcumByCurrency = totalAmountsAcumByCurrency;
    }
}