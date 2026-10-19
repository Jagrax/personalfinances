package ar.com.personalfinances.service.creditcard;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CreditCardStatementResolver {

    private final Map<CreditCardStatementFormat, CreditCardStatement> statements = new EnumMap<>(CreditCardStatementFormat.class);

    public CreditCardStatementResolver(List<CreditCardStatement> statements) {
        for (CreditCardStatement statement : statements) {
            this.statements.put(statement.getFormat(), statement);
        }
    }

    public CreditCardStatement resolve(CreditCardStatementFormat format) {
        final CreditCardStatement statement = statements.get(format);
        if (statement == null) {
            throw new IllegalArgumentException("Formato de resumen no soportado: " + format);
        }
        return statement;
    }
}
