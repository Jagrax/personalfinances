package ar.com.personalfinances.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ar.com.personalfinances.entity.Expense;

@Repository
public interface DashboardRepository extends JpaRepository<Expense, Long> {

	String EXCLUDE_CARD_IMPUESTOS = """
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etv JOIN tags tv ON tv.id = etv.tag_id
			    WHERE etv.expense_id = e.id AND tv.name = 'Impuestos' AND a.type = 'CREDIT_CARD'
			  )
			""";

	String EXCLUDE_CARD_PAYMENTS = """
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etp JOIN tags tp ON tp.id = etp.tag_id
			    WHERE etp.expense_id = e.id AND tp.name = 'Pago de tarjeta' AND a.type = 'CREDIT_CARD'
			  )
			""";

	String EXCLUDE_THIRD_PARTY = """
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags et3 JOIN tags t3 ON t3.id = et3.tag_id
			    WHERE et3.expense_id = e.id AND t3.name IN ('Gastos de Oficina', 'Gastos de 3ro')
			      AND a.type = 'CREDIT_CARD'
			  )
			""";

	String EXCLUDE_GALICIA_INVERSION = """
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags eti JOIN tags ti ON ti.id = eti.tag_id
			    WHERE eti.expense_id = e.id AND ti.name = 'Inversión'
			      AND (a.sync_provider = 'GALICIA' OR a.subtype = 'BANCO_GALICIA' OR UPPER(a.name) LIKE '%GALICIA%')
			  )
			""";

	String EXCLUDE_GALICIA_CARD_PAYMENTS = """
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etgp JOIN tags tgp ON tgp.id = etgp.tag_id
			    WHERE etgp.expense_id = e.id AND tgp.name = 'Pago de tarjeta'
			      AND (a.sync_provider = 'GALICIA' OR a.subtype = 'BANCO_GALICIA' OR UPPER(a.name) LIKE '%GALICIA%')
			  )
			""";

	String EXCLUDE_GALICIA_OFFICE = """
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etg3 JOIN tags tg3 ON tg3.id = etg3.tag_id
			    WHERE etg3.expense_id = e.id AND tg3.name = 'Gastos de Oficina'
			      AND (a.sync_provider = 'GALICIA' OR a.subtype = 'BANCO_GALICIA' OR UPPER(a.name) LIKE '%GALICIA%')
			  )
			""";

	String EXCLUDE_GALICIA_CASH_WITHDRAWAL = """
			  AND NOT (COALESCE(e.description, '') = 'Extraccion en autoservicio'
			    AND NOT EXISTS (SELECT 1 FROM expense_items ei WHERE ei.expense_id = e.id)
			    AND (a.sync_provider = 'GALICIA' OR a.subtype = 'BANCO_GALICIA' OR UPPER(a.name) LIKE '%GALICIA%'))
			""";

	String EXCLUDE_GALICIA_OWN_TRANSFERS = """
			  AND NOT (COALESCE(e.description, '') = 'Transferencia a Cta. Brubank'
			    AND (a.sync_provider = 'GALICIA' OR a.subtype = 'BANCO_GALICIA' OR UPPER(a.name) LIKE '%GALICIA%'))
			""";

	String EXCLUSIONS_CARDS = EXCLUDE_CARD_IMPUESTOS + EXCLUDE_CARD_PAYMENTS + EXCLUDE_THIRD_PARTY;

	String EXCLUSIONS_MAIN_ACCOUNTS = EXCLUSIONS_CARDS + EXCLUDE_GALICIA_INVERSION + EXCLUDE_GALICIA_CARD_PAYMENTS
			+ EXCLUDE_GALICIA_OFFICE + EXCLUDE_GALICIA_CASH_WITHDRAWAL + EXCLUDE_GALICIA_OWN_TRANSFERS;

	/**
	 * Importe con el que el gasto suma en los totales. Un gasto en cero con items es plata que se pagó sin un
	 * movimiento propio en la cuenta (el efectivo que ya se tenía en la mano, por ejemplo): en ese caso el gasto es
	 * la suma de sus items, que es donde está el desglose de a dónde fue esa plata. Sin esto el retiro de cajero se
	 * descarta como traspaso y el gasto de cero no suma nada, así que el gasto real se pierde del dashboard.
	 */
	String EXPENSE_SPENDING_AMOUNT = """
			CASE
				WHEN e.amount = 0 AND EXISTS (SELECT 1 FROM expense_items ei WHERE ei.expense_id = e.id)
					THEN COALESCE((SELECT SUM(ei.amount) FROM expense_items ei WHERE ei.expense_id = e.id), 0)
				WHEN a.type = 'BANK_ACCOUNT' AND e.amount < 0 THEN -e.amount
				WHEN a.type = 'CREDIT_CARD' THEN e.amount
				ELSE 0
			END
			""";

	/**
	 * Cuentas y gastos que entran en los totales. El gasto en cero con items entra aunque no sea negativo: es la
	 * contrapartida de la convención de EXPENSE_SPENDING_AMOUNT.
	 */
	String EXPENSE_SCOPE = """
			((a.type = 'BANK_ACCOUNT' AND UPPER(a.name) LIKE '%GALICIA%'
				AND (e.amount < 0 OR (e.amount = 0 AND EXISTS (SELECT 1 FROM expense_items ei WHERE ei.expense_id = e.id))))
				OR (a.type = 'CREDIT_CARD' AND TRIM(a.name) IN ('VISA', 'Master Card')))
			""";

	String EFFECTIVE_DATE = """
			CASE
				WHEN a.type = 'CREDIT_CARD' AND e.details REGEXP 'Cuota [0-9]+ de [0-9]+'
				  AND cp.period_start IS NOT NULL AND cp.closing_date IS NOT NULL
				THEN GREATEST(e.date, DATE_ADD(cp.period_start, INTERVAL FLOOR(DATEDIFF(cp.closing_date, cp.period_start) / 2) DAY))
				ELSE e.date
			END
			""";

	@Query(value = """
			SELECT COALESCE(SUM(""" + EXPENSE_SPENDING_AMOUNT + """
			), 0)
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			  AND """ + EXPENSE_SCOPE + EXCLUSIONS_MAIN_ACCOUNTS, nativeQuery = true)
	BigDecimal getTotalForPeriod(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query(value = """
			SELECT COALESCE(SUM(e.amount), 0)
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			  AND a.type = 'CREDIT_CARD'
			  AND e.amount > 0
			""" + EXCLUSIONS_CARDS, nativeQuery = true)
	BigDecimal getCardTotalForPeriod(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query(value = """
			SELECT
				YEAR(x.effective_date) as year,
				MONTH(x.effective_date) as month,
				x.accountName,
				COALESCE(SUM(x.signedAmount), 0) as total
			FROM (
				SELECT
					""" + EFFECTIVE_DATE + """
					as effective_date,
					a.name as accountName,
					""" + EXPENSE_SPENDING_AMOUNT + """
					as signedAmount
				FROM expenses e
				JOIN accounts a ON a.id = e.account_id
				LEFT JOIN card_periods cp ON cp.id = e.period_id
				WHERE a.owner_id = :userId
				  AND """ + EXPENSE_SCOPE + EXCLUSIONS_MAIN_ACCOUNTS + """
			) x
			WHERE x.effective_date >= :fromDate
			  AND x.effective_date <= :toDate
			GROUP BY YEAR(x.effective_date), MONTH(x.effective_date), x.accountName
			ORDER BY year, month, accountName
			""", nativeQuery = true)
	List<Object[]> getMonthlySeriesByAccount(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

	@Query(value = """
			SELECT
				e.id,
				e.date,
				TRIM(COALESCE(e.description, e.original_description, '')),
				e.details,
				(SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ', ')
				   FROM expense_tags et2 JOIN tags t ON t.id = et2.tag_id
				  WHERE et2.expense_id = e.id),
				""" + EXPENSE_SPENDING_AMOUNT + """
				as signedAmount,
				a.type as accountType
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND a.name = :accountName
			  AND """ + EXPENSE_SCOPE + """
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			""" + EXCLUSIONS_MAIN_ACCOUNTS + """
			ORDER BY e.date, e.id
			""", nativeQuery = true)
	List<Object[]> getSegmentExpenses(@Param("userId") Long userId, @Param("accountName") String accountName,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query(value = """
			SELECT 
				TRIM(COALESCE(e.description, e.original_description)) as merchant,
				COALESCE(SUM(""" + EXPENSE_SPENDING_AMOUNT + """
				), 0) as total,
				COUNT(*) as count
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			  AND """ + EXPENSE_SCOPE + """
			  AND TRIM(COALESCE(e.description, e.original_description)) NOT IN (
			    'Suscripción FIMA', 'Pago de tarjeta Master Card', 'Pago tarjeta VISA', 'Extraccion en autoservicio'
			  )
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etx JOIN tags tx ON tx.id = etx.tag_id
			    WHERE etx.expense_id = e.id AND tx.name = 'Gastos de Oficina'
			  )
			""" + EXCLUSIONS_MAIN_ACCOUNTS + """
			GROUP BY TRIM(COALESCE(e.description, e.original_description))
			""", nativeQuery = true)
	List<Object[]> getTopMerchants(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query(value = """
			SELECT 
				TRIM(SUBSTRING_INDEX(TRIM(COALESCE(e.description, e.original_description)), ' - ', 1)) as merchant,
				COALESCE(SUM(""" + EXPENSE_SPENDING_AMOUNT + """
				), 0) as total,
				COUNT(*) as count
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			  AND """ + EXPENSE_SCOPE + """
			  AND TRIM(COALESCE(e.description, e.original_description)) NOT IN (
			    'Suscripción FIMA', 'Pago de tarjeta Master Card', 'Pago tarjeta VISA', 'Extraccion en autoservicio'
			  )
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etx JOIN tags tx ON tx.id = etx.tag_id
			    WHERE etx.expense_id = e.id AND tx.name = 'Gastos de Oficina'
			  )
			""" + EXCLUSIONS_MAIN_ACCOUNTS + """
			GROUP BY TRIM(SUBSTRING_INDEX(TRIM(COALESCE(e.description, e.original_description)), ' - ', 1))
			""", nativeQuery = true)
	List<Object[]> getTopMerchantGroups(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query(value = """
			SELECT COUNT(*)
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			  AND """ + EXPENSE_SCOPE + """
			  AND TRIM(COALESCE(e.description, e.original_description)) NOT IN (
			    'Suscripción FIMA', 'Pago de tarjeta Master Card', 'Pago tarjeta VISA', 'Extraccion en autoservicio'
			  )
			  AND NOT EXISTS (
			    SELECT 1 FROM expense_tags etx JOIN tags tx ON tx.id = etx.tag_id
			    WHERE etx.expense_id = e.id AND tx.name = 'Gastos de Oficina'
			  )
			""" + EXCLUSIONS_MAIN_ACCOUNTS, nativeQuery = true)
	long countMovementsForPeriod(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query(value = """
			SELECT 
				t.id, t.name, t.color,
				COALESCE(SUM(""" + EXPENSE_SPENDING_AMOUNT + """
				), 0) as total
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			JOIN expense_tags et ON et.expense_id = e.id
			JOIN tags t ON t.id = et.tag_id
			LEFT JOIN card_periods cp ON cp.id = e.period_id
			WHERE a.owner_id = :userId
			  AND
			""" + EFFECTIVE_DATE + """
			  BETWEEN :from AND :to
			  AND """ + EXPENSE_SCOPE + """
			""" + EXCLUSIONS_MAIN_ACCOUNTS + """
			GROUP BY t.id, t.name, t.color
			ORDER BY total DESC
			LIMIT 10
			""", nativeQuery = true)
	List<Object[]> getTopTags(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	String RECURRING_TAGS = "('Alquiler', 'Expensas', 'Servicio', 'Hogar', 'Celular', 'Seguro', 'Deporte/Actividad')";

	/**
	 * Gastos recurrentes de los últimos 12 meses, una fila por pago individual.
	 *
	 * Los pagos en efectivo se anotan como items de un gasto en cero (o de la extracción con la que se sacó la
	 * plata), así que los items aportan su importe y sus tags con la fecha del gasto padre: sin eso el alquiler
	 * pagado en efectivo no existe como gasto, porque el retiro de cajero es un traspaso y no un gasto. El tag
	 * Alquiler también se usa para clasificar otros gastos del hogar (un flete, por ejemplo), por eso solo se
	 * relabela como "Alquiler" cuando la descripción es una transferencia al dueño; el resto queda con su
	 * descripción y, si no es recurrente, no entra.
	 *
	 * Las transferencias a terceros se excluyen salvo que sean el alquiler: pagarle a alguien la cancha de los
	 * domingos o una parte de una juntada se repite, pero no es un gasto fijo del hogar.
	 *
	 * Además de aparecer en 7 de los 12 meses, entra un concepto con al menos 2 pagos en los últimos 3 meses: los
	 * servicios dados de alta hace poco (un cambio de ISP) todavía no llegan al corte pero ya son un fijo. El corte
	 * en 7 deja afuera compras repetidas de supermercado/delivery, y las cuotas (Cuota X de Y) se excluyen porque
	 * terminan.
	 */
	@Query(value = """
			WITH recurring AS (
				SELECT CASE WHEN EXISTS (
						SELECT 1 FROM expense_tags era JOIN tags tra ON tra.id = era.tag_id
						WHERE era.expense_id = e.id AND tra.name = 'Alquiler'
					) AND TRIM(COALESCE(e.description, '')) LIKE 'Transferencia%'
					THEN 'Alquiler'
					ELSE TRIM(COALESCE(e.description, e.original_description)) END AS concept,
					e.date AS date,
					CASE WHEN a.type = 'BANK_ACCOUNT' THEN -e.amount ELSE e.amount END AS amount
				FROM expenses e
				JOIN accounts a ON a.id = e.account_id
				WHERE a.owner_id = :userId
				  AND e.date BETWEEN :from AND :to
				  AND ((a.type = 'BANK_ACCOUNT' AND UPPER(a.name) LIKE '%GALICIA%' AND e.amount < 0)
				    OR (a.type = 'CREDIT_CARD' AND TRIM(a.name) IN ('VISA', 'Master Card')))
				  AND EXISTS (
				    SELECT 1 FROM expense_tags et JOIN tags t ON t.id = et.tag_id
				    WHERE et.expense_id = e.id
				      AND t.name IN """ + RECURRING_TAGS + """
				  )
				  AND (TRIM(COALESCE(e.description, '')) NOT LIKE 'Transferencia%'
				    OR EXISTS (
				      SELECT 1 FROM expense_tags era JOIN tags tra ON tra.id = era.tag_id
				      WHERE era.expense_id = e.id AND tra.name = 'Alquiler'
				    ))
				  AND (e.details IS NULL OR e.details NOT REGEXP 'Cuota [0-9]+ de [0-9]+')
				  AND NOT EXISTS (
				    SELECT 1 FROM expense_tags etx JOIN tags tx ON tx.id = etx.tag_id
				    WHERE etx.expense_id = e.id
				      AND tx.name IN ('Inversión', 'Pago de tarjeta', 'Gastos de Oficina', 'Gastos de 3ro', 'Impuestos', 'Prestamo')
				  )
			""" + EXCLUSIONS_MAIN_ACCOUNTS + """
				UNION ALL
				SELECT CASE WHEN EXISTS (
						SELECT 1 FROM expense_item_tags eia JOIN tags tia ON tia.id = eia.tag_id
						WHERE eia.expense_item_id = i.id AND tia.name = 'Alquiler'
					) THEN 'Alquiler'
					ELSE TRIM(i.description) END AS concept,
					e.date AS date,
					i.amount AS amount
				FROM expense_items i
				JOIN expenses e ON e.id = i.expense_id
				JOIN accounts a ON a.id = e.account_id
				WHERE a.owner_id = :userId
				  AND e.date BETWEEN :from AND :to
				  AND ((a.type = 'BANK_ACCOUNT' AND UPPER(a.name) LIKE '%GALICIA%')
				    OR (a.type = 'CREDIT_CARD' AND TRIM(a.name) IN ('VISA', 'Master Card')))
				  AND i.amount > 0
				  AND EXISTS (
				    SELECT 1 FROM expense_item_tags eit JOIN tags t ON t.id = eit.tag_id
				    WHERE eit.expense_item_id = i.id
				      AND t.name IN """ + RECURRING_TAGS + """
				  )
			""" + EXCLUSIONS_MAIN_ACCOUNTS + """
			)
			SELECT r.concept, r.date, r.amount
			FROM recurring r
			JOIN (
				SELECT concept FROM recurring
				GROUP BY concept
				HAVING COUNT(DISTINCT DATE_FORMAT(date, '%Y-%m')) >= 7
				    OR COUNT(DISTINCT CASE WHEN date >= DATE_FORMAT(DATE_SUB(:to, INTERVAL 2 MONTH), '%Y-%m-01')
				                           THEN DATE_FORMAT(date, '%Y-%m') END) >= 2
			) c ON c.concept = r.concept
			ORDER BY r.concept, r.date DESC
			""", nativeQuery = true)
	List<Object[]> getRecurringExpenses(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query(value = """
			SELECT a.id, a.name, 
			       COALESCE(SUM(e.amount), 0) as total,
			       cp.closing_date, cp.due_date, a.icon
			FROM expenses e
			JOIN accounts a ON a.id = e.account_id
			JOIN card_periods cp ON cp.account_id = a.id
			WHERE a.owner_id = :userId
			  AND a.type = 'CREDIT_CARD'
			  AND cp.status = 'OPEN'
			  AND (
			    (e.period_id IS NOT NULL AND e.period_id = cp.id) OR
			    (e.period_id IS NULL AND e.date >= COALESCE(cp.period_start, DATE(cp.closing_date) - INTERVAL 1 MONTH) AND e.date <= cp.closing_date)
			  )
			  AND e.amount > 0
			""" + EXCLUSIONS_CARDS + """
			GROUP BY a.id, a.name, cp.closing_date, cp.due_date, a.icon
			ORDER BY a.name
			""", nativeQuery = true)
	List<Object[]> getOpenPeriodCardConsumptionByAccount(@Param("userId") Long userId);

	@Query(value = """
			SELECT a.id, a.name, a.currency, a.sync_provider, a.icon, a.bank_id, a.last_sync_at,
			       COALESCE(SUM(""" + EXPENSE_SPENDING_AMOUNT + """
			       ), 0) as total
			FROM accounts a
			LEFT JOIN banks b ON b.id = a.bank_id
			LEFT JOIN expenses e ON e.account_id = a.id AND e.date BETWEEN :from AND :to
			WHERE a.owner_id = :userId
			  AND a.type = 'BANK_ACCOUNT'
			  AND a.currency = 'ARS'
			  AND (a.sync_provider = 'GALICIA' OR a.subtype = 'BANCO_GALICIA' OR UPPER(b.name) LIKE '%GALICIA%')
			""" + EXCLUDE_GALICIA_INVERSION + EXCLUDE_GALICIA_CARD_PAYMENTS + EXCLUDE_GALICIA_OFFICE + EXCLUDE_GALICIA_CASH_WITHDRAWAL + EXCLUDE_GALICIA_OWN_TRANSFERS + """
			GROUP BY a.id, a.name, a.currency, a.sync_provider, a.icon, a.bank_id, a.last_sync_at
			ORDER BY a.name
			""", nativeQuery = true)
	List<Object[]> getGaliciaAccountExpenses(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query(value = """
			SELECT a.id, a.name, a.currency, a.sync_provider, a.icon, a.bank_id, a.last_sync_at,
			       COALESCE(SUM(e.amount), 0) as total
			FROM accounts a
			LEFT JOIN expenses e ON e.account_id = a.id
			WHERE a.owner_id = :userId
			  AND UPPER(a.name) = 'SDD'
			GROUP BY a.id, a.name, a.currency, a.sync_provider, a.icon, a.bank_id, a.last_sync_at
			ORDER BY a.name
			""", nativeQuery = true)
	List<Object[]> getSddAccountExpenses(@Param("userId") Long userId);
}
