# AGENTS.md

## Reglas de trabajo (IMPORTANTES)

- **NO compilar con maven ni por consola.** El usuario ejecuta y prueba desde IntelliJ. Tras hacer cambios de código, no correr `mvn compile`/`mvnw` ni verificar builds; el usuario revisa el código y lo prueba él mismo.
- No crear/editar tablas ni datos en la DB sin avisar y sin que el usuario lo pida.
- Comunicación en español, terse y directa.
- No agregar comentarios al código salvo que se pidan (`Code style` del sistema).
- No tocar credenciales; no escribirlas en archivos versionados.

## Proyecto

Aplicación web personal de finanzas (MVC server-side, no SPA). Sincroniza movimientos de cuentas bancarias y tarjetas de crédito de Banco Galicia, mapea descripciones del banco a gastos normalizados con tags, y genera reportes/gráficos.

- Base package: `ar.com.personalfinances`
- Packaging: WAR para WildFly. Spring Boot 3.5.14, Java 21.
- Java version configurada en `pom.xml` (property `java.version` → `maven.compiler.release`).
- Dependencias clave: spring-boot-starter-web/data-jpa/security/thymeleaf/webflux/validation, mysql-connector-j, de.focus-shift.jollyday (feriados AR), cron-utils, lombok, pdfbox, jsoup, jackson-datatype-jsr310.

## Entorno / Config

- **Datasource**: JNDI `java:/personalfinancesDS` definido en `src/main/webapp/WEB-INF/personalfinances-ds.xml` (gitignored; credenciales SOLO ahí, nunca duplicadas). Para ambiente local los datos apuntan a DB MySQL.
- **BD**: MySQL 8 local. Existen BD `pfindesa` (dev) y `pfinprod` (prod). Cliente MySQL instalado en `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe` (usar solo si hace falta consultar; preguntar antes de modificar datos).
- `src/main/resources/application-wildfly.properties`: profile `wildfly`, `ddl-auto=validate` (no altera schema), context-path `/personalfinances`, formatos dd/MM/yyyy. **No hay `application.properties` default**.
- `src/main/webapp/WEB-INF/jboss-deployment-structure.xml`: excluye módulos JBoss (SLF4J, Hibernate, Jackson) para que el WAR use sus versiones.
- Perfil maven `release` (se activa con `-DskipTests`): excluye `personalfinances-ds.xml` y copia el WAR a `C:\wildfly-33.0.1.Final-pfinprod\standalone\deployments` vía maven-antrun-plugin.
- Migraciones SQL en `db/` (raíz) y `src/main/resources/db/`. `db/` y `personalfinances-ds.xml` están gitignored.

## Arquitectura (paquetes principales)

- `entity`: Expense, ExpenseMapping, Account, User, Tag, ExpenseItem, ExpensesGroup, AlertEvent, Bank + enums (AccountType, Role, SyncProvider, etc.). Nota: `Category` está `@Deprecated`.
- `repository`: Spring Data JPA + queries `@Query` nativas (resúmenes por tag/período en ExpenseRepository) + `JpaSpecificationExecutor`.
- `service`: lógica de negocio. Inyección por constructor. Interfaces + Impl para algunos servicios; otros son clases concretas (ExpenseMappingService, ExpenseService).
- `controller` + `controller.abm`: Thymeleaf MVC. `@ResponseBody` para AJAX.
- `api.galicia`: connector HTTP + modelos del homebanking de Galicia (BankAccountMovement, Consumption). `webclient`: RestConnector/RestSecurityManager (cliente HTTP genérico).
- `configuration`: Security (CSRF deshabilitado), Jackson, cache, ApplicationProperties.
- `util`: DateUtils, CommonResult, SyncResult, NumberUtils, ApplicationUtils, POJOs de search, Menu/MenuItem, DTOs de chart. `task`: scheduler dinámico + DbBackupService.
- `ignored/` (fuera del package): código deshabilitado (SharedExpense).

## Conceptos de dominio clave

- **Expense** (`expenses`): `date` (LocalDate), `description` (normalizada), `originalDescription` (cruda del banco, NO editable desde la app), `amount` (BigDecimal, negativo = débito), `details`, `comments`, `tags` (ManyToMany), `account`, `user`, `items`.
- **ExpenseMapping** (`expense_mappings`): regla para normalizar una descripción del banco. Campos: `bankDescription` (match exacto, ignorecase), `regexPattern` (match por regex), `normalizedDescription` (admitE capture groups: `$1`, `${camel:1}` — camelCase a Title Case), `details`, `tags`, `enabled`. Match en `ExpenseMappingService`: regex primero, luego exacto.
- **Account** (`accounts`): `owner`, `name`, `type` (enum), `subtype` (string libre, ej "BANCO_GALICIA"), `currency`, `syncEnabled`, `syncProvider` (GALICIA), `closingDay` (cierre tarjeta), `lastSyncAt`, `externalAccountId`, `icon`. Unique `(owner_id, type, name)`.
- Descripción de movimiento: `getDescription()` combina `descripcionSide` + ` | ` + `descripcionAMostrar` si difieren; el amount se toma de débito si no es cero, si no crédito. Montos vienen en formato argentino `1.234,56`.

## Sincronización bancaria (AccountManagementServiceImpl)

- `syncAccountMovements` (cuentas) y `syncCreditCardAccountMovements` (tarjetas): el filtro de movimientos ya existentes busca por `cuenta + fecha + importe`, retrocede por fines de semana/feriados (`DateUtils.isWeekend`/`esFeriado`), y además exige que `originalDescription` coincida (`descriptionMatches` + `movementStableKey`, que compara los primeros 3 campos estables: header, nombre del tercero y CUIT/DNI) para no "consumir" un gasto que corresponde a otro movimiento cuando hay varios con misma fecha/importe. Los que no existen se crean con auto-mapping (`createExpense` → `ExpenseMappingService.resolveNormalizedDescription`).
- `learnFromBankMovements`: descubre descripciones repetidas sin mapping (para proponer ExpenseMappings). Se usa `StringUtils.hasText` para validaciones.
- SyncRequiere cookies de Galicia pegadas por el usuario en la UI (`galicia-connect.html`); flujo SSO en `GaliciaApiConnector.establishCuentasSession`.

## Convenciones del código

- Comentarios y mensajes de UI/log: **español**. Identificadores (clases/métodos/variables): **inglés**.
- Logging SLF4J con lombok `@Slf4j` y prefijo de contexto en corchetes: `[syncBankAccount]`, `[getMovimientosCuenta]`, `[isValid]`, etc. `log.info` operaciones, `log.warn` recuperables, `log.error` fallos.
- Lombok: `@Getter/@Setter`, `@RequiredArgsConstructor` (inyección por constructor), `@Slf4j`.
- Tablas/columnas DB: snake_case. Enums: SCREAMING_SNAKE_CASE.
- Controllers Thymeleaf, paginación manual (listas + `getItemsPaginated`, no Pageable en controllers), Specifications para filtros dinámicos.
- Transacciones con `jakarta.transaction.Transactional` (`@Transactional`).

## Git / release

- Rama única `master`, sin CI/CD. Formato de commit: `[version] Descripcion` (ej. `[2.3.9] Improve expense mapping regex mechanism`). Versión actual del pom: `2.3.9`.
- Solo commitear cuando el usuario lo pida explícitamente.

## Frontend

- Thymeleaf + Bootstrap 5 + jQuery. Templates en `src/main/resources/templates/` (fragments/layout.html, abm/*, dashboard, reports, auth, galicia-connect, report-expenses).
- Estáticos en `src/main/resources/static/`. Login/registro públicos; resto autenticado.

## Backups / tareas

- `DbBackupService`: `mysqldump` a `db/backups/` con deduplicación por SHA-256, configurable vía `InstanceTask` (scheduler dinámico con cron QUARTZ). Backup local a cons. de `restic`/otro no configurado.