package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.ExpenseMapping;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.ExpenseMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExpenseMappingService {

    private final ExpenseMappingRepository repository;

    public Optional<ExpenseMapping> matchExpenseMapping(User user, String rawDescription) {
        if (!StringUtils.hasText(rawDescription)) return Optional.empty();

        String desc = rawDescription.trim();

        // 1. Regex match
        for (ExpenseMapping mapping : repository.findAllByUserAndRegexPatternIsNotNull(user)) {
            try {
                Pattern pattern = Pattern.compile(mapping.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(desc).matches()) {
                    return Optional.of(mapping);
                }
            } catch (Exception e) {
                // Log y continuar con los demás
                System.err.println("Regex inválido para mapping id=" + mapping.getId() + ": " + e.getMessage());
            }
        }

        // 2. Exact match
        return repository.findFirstByUserAndBankDescriptionIgnoreCase(user, desc);
    }
}