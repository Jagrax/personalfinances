package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.ExpenseMapping;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.ExpenseMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExpenseMappingService {

    private final ExpenseMappingRepository repository;

    public Optional<ExpenseMapping> matchExpenseMapping(User user, String rawDescription) {
        if (!StringUtils.hasText(rawDescription)) return Optional.empty();

        String desc = rawDescription.trim();

        // 1. Regex match
        for (ExpenseMapping mapping : repository.findAllByUserAndRegexPatternIsNotNullAndEnabledTrue(user)) {
            if (!StringUtils.hasText(mapping.getRegexPattern())) continue;

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
        return repository.findFirstByUserAndBankDescriptionIgnoreCaseAndEnabledTrue(user, desc);
    }

    public String resolveNormalizedDescription(ExpenseMapping mapping, String rawDescription) {
        if (!StringUtils.hasText(mapping.getNormalizedDescription())) {
            return rawDescription;
        }

        if (!StringUtils.hasText(mapping.getRegexPattern()) || !StringUtils.hasText(rawDescription)) {
            return mapping.getNormalizedDescription();
        }

        try {
            Pattern pattern = Pattern.compile(mapping.getRegexPattern(), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(rawDescription.trim());
            if (matcher.matches()) {
                String result = mapping.getNormalizedDescription();
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    String group = matcher.group(i);
                    if (group != null) {
                        result = result.replace("${camel:" + i + "}", toCamelCase(group.trim()));
                        result = result.replace("$" + i, group.trim());
                    }
                }
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error al resolver capture groups para mapping id=" + mapping.getId() + ": " + e.getMessage());
        }

        return mapping.getNormalizedDescription();
    }

    private String toCamelCase(String input) {
        if (!StringUtils.hasText(input)) return input;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(' ');
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }
}
