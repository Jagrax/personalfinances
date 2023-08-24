package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.Expense;
import lombok.Getter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class ExpensePage extends PageImpl<Expense> {

    private final BigDecimal totalAmountsAcum;
    private final BigDecimal onPageTotalAmountsAcum;

    public ExpensePage(List<Expense> content, Pageable pageable, long total, BigDecimal totalAmountsAcum, BigDecimal onPageTotalAmountsAcum) {
        super(content, pageable, total);
        this.onPageTotalAmountsAcum = onPageTotalAmountsAcum;
        this.totalAmountsAcum = totalAmountsAcum;
    }
}