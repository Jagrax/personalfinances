package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountSubtype;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.entity.SyncProvider;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.util.CommonResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GaliciaSyncServiceImpl implements GaliciaSyncService {

    private final GaliciaApiService galiciaApiService;
    private final AccountManagementService accountManagementService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public GaliciaSyncServiceImpl(GaliciaApiService galiciaApiService, AccountManagementService accountManagementService, AccountRepository accountRepository) {
        this.galiciaApiService = galiciaApiService;
        this.accountManagementService = accountManagementService;
        this.accountRepository = accountRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CommonResult discoverAccounts(String skywalkerToken) {
        try {
            String onlinebankingCookies = (String) galiciaApiService.establishSession(skywalkerToken).getPayload();
            if (!StringUtils.hasText(onlinebankingCookies)) {
                return CommonResult.error("No se pudo establecer sesion en onlinebanking con el token provisto");
            }

            CommonResult cardsResult = galiciaApiService.getCardsOverview(skywalkerToken);
            CommonResult accountsResult = galiciaApiService.getSeccionMisCuentas(onlinebankingCookies);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("onlinebankingCookies", onlinebankingCookies);
            result.put("cardsRawJson", cardsResult.isError() ? null : cardsResult.getPayload());
            result.put("accountsRawJson", accountsResult.isError() ? null : accountsResult.getPayload());

            return CommonResult.ok(result);
        } catch (Exception e) {
            log.error("[discoverAccounts] Error al descubrir cuentas", e);
            return CommonResult.error("Error al descubrir cuentas: " + e.getMessage());
        }
    }

    @Override
    public CommonResult discoverAccountsWithCookies(String cookies) {
        return discoverAccountsWithCookies(null, cookies);
    }

    @Override
    public CommonResult discoverAccountsWithCookies(User user, String cookies) {
        try {
            String skywalkerToken = extractSkywalkerFromCookies(cookies);
            if (!StringUtils.hasText(skywalkerToken)) {
                return CommonResult.error("No se encontro el token Skywalker en las cookies provistas");
            }

            CommonResult cardsResult = galiciaApiService.getCardsOverview(skywalkerToken);
            CommonResult accountsResult = galiciaApiService.getSeccionMisCuentas(cookies);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("onlinebankingCookies", cookies);
            result.put("cardsRawJson", cardsResult.isError() ? null : cardsResult.getPayload());
            result.put("accountsRawJson", accountsResult.isError() ? null : accountsResult.getPayload());

            if (user != null) {
                List<Account> userAccounts = accountRepository.findByOwner(user);
                List<Map<String, Object>> existingAccounts = userAccounts.stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("name", a.getName());
                    m.put("type", a.getType().name());
                    return m;
                }).collect(Collectors.toList());
                result.put("existingAccounts", existingAccounts);

                Set<String> importedCardIds = userAccounts.stream()
                    .filter(a -> AccountType.CREDIT_CARD.equals(a.getType()) && StringUtils.hasText(a.getExternalAccountId()))
                    .map(Account::getExternalAccountId)
                    .collect(Collectors.toSet());
                result.put("importedCardIds", importedCardIds);

                Set<String> importedAccountIds = userAccounts.stream()
                    .filter(a -> AccountType.BANK_ACCOUNT.equals(a.getType()) && StringUtils.hasText(a.getExternalAccountId()))
                    .map(Account::getExternalAccountId)
                    .collect(Collectors.toSet());
                result.put("importedAccountIds", importedAccountIds);
            }

            return CommonResult.ok(result);
        } catch (Exception e) {
            log.error("[discoverAccountsWithCookies] Error al descubrir cuentas con cookies", e);
            return CommonResult.error("Error al descubrir cuentas: " + e.getMessage());
        }
    }

    @Override
    public CommonResult importSelectedAccounts(User user, String accountsJson) {
        try {
            JsonNode root = objectMapper.readTree(accountsJson);
            List<Account> processedAccounts = new ArrayList<>();

            if (root.has("creditCards") && root.get("creditCards").isArray()) {
                for (JsonNode card : root.get("creditCards")) {
                    String accountNumber = card.has("credit_account_number") ? card.get("credit_account_number").asText() : null;
                    String brand = card.has("brand") ? card.get("brand").asText() : null;
                    if (!StringUtils.hasText(accountNumber) || !StringUtils.hasText(brand)) continue;

                    if (card.has("existingAccountId") && !card.get("existingAccountId").isNull()) {
                        Long existingId = card.get("existingAccountId").asLong();
                        Optional<Account> opt = accountRepository.findById(existingId);
                        if (opt.isPresent()) {
                            Account existing = opt.get();
                            existing.setExternalAccountId(accountNumber);
                            existing.setSyncProvider(SyncProvider.GALICIA);
                            existing.setSyncEnabled(true);
                            accountRepository.save(existing);
                            processedAccounts.add(existing);
                            log.info("[importSelectedAccounts] Tarjeta de credito asociada a cuenta existente {} (id={})", existing.getName(), existingId);
                        }
                        continue;
                    }

                    String lastFour = card.has("card_number_last_four_digits") ? card.get("card_number_last_four_digits").asText() : null;
                    String accountName;
                    if ("VISA".equalsIgnoreCase(brand)) {
                        accountName = "VISA";
                    } else if ("MASTERCARD".equalsIgnoreCase(brand) || "MASTER".equalsIgnoreCase(brand)) {
                        accountName = "Master Card";
                    } else {
                        accountName = brand + (lastFour != null ? " (" + lastFour + ")" : "");
                    }

                    Account account = new Account();
                    account.setOwner(user);
                    account.setName(accountName);
                    account.setType(AccountType.CREDIT_CARD);
                    account.setSubtype(AccountSubtype.BANCO_GALICIA.name());
                    account.setSyncProvider(SyncProvider.GALICIA);
                    account.setExternalAccountId(accountNumber);
                    account.setSyncEnabled(true);
                    processedAccounts.add(accountRepository.save(account));
                    log.info("[importSelectedAccounts] Tarjeta de credito creada: {} con externalAccountId {}", accountName, accountNumber);
                }
            }

            if (root.has("bankAccounts") && root.get("bankAccounts").isArray()) {
                for (JsonNode bankAccount : root.get("bankAccounts")) {
                    String accountId = bankAccount.has("id") ? bankAccount.get("id").asText() : null;

                    if (bankAccount.has("existingAccountId") && !bankAccount.get("existingAccountId").isNull()) {
                        Long existingId = bankAccount.get("existingAccountId").asLong();
                        Optional<Account> opt = accountRepository.findById(existingId);
                        if (opt.isPresent()) {
                            Account existing = opt.get();
                            existing.setExternalAccountId(accountId);
                            existing.setSyncProvider(SyncProvider.GALICIA);
                            existing.setSyncEnabled(true);
                            accountRepository.save(existing);
                            processedAccounts.add(existing);
                            log.info("[importSelectedAccounts] Cuenta bancaria asociada a cuenta existente {} (id={})", existing.getName(), existingId);
                        }
                        continue;
                    }

                    String accountName = bankAccount.has("name") ? bankAccount.get("name").asText() : "Galicia Bank Account";
                    String currency = bankAccount.has("currency") ? bankAccount.get("currency").asText() : "ARS";

                    Account account = new Account();
                    account.setOwner(user);
                    account.setName(accountName);
                    account.setType(AccountType.BANK_ACCOUNT);
                    account.setSubtype(AccountSubtype.BANCO_GALICIA.name());
                    account.setCurrency(currency);
                    account.setSyncProvider(SyncProvider.GALICIA);
                    account.setExternalAccountId(accountId);
                    account.setSyncEnabled(true);
                    processedAccounts.add(accountRepository.save(account));
                    log.info("[importSelectedAccounts] Cuenta bancaria creada: {} con externalAccountId {}", accountName, accountId);
                }
            }

            return CommonResult.ok(processedAccounts, "Se procesaron " + processedAccounts.size() + " cuentas");
        } catch (Exception e) {
            log.error("[importSelectedAccounts] Error al importar cuentas", e);
            return CommonResult.error("Error al importar cuentas: " + e.getMessage());
        }
    }

    @Override
    public CommonResult syncAllAccounts(User user, String skywalkerToken) {
        try {
            String onlinebankingCookies = (String) galiciaApiService.establishSession(skywalkerToken).getPayload();
            if (!StringUtils.hasText(onlinebankingCookies)) {
                return CommonResult.error("No se pudo establecer sesion en onlinebanking con el token provisto");
            }

            String cuentasCookies = (String) galiciaApiService.establishCuentasSession(onlinebankingCookies).getPayload();

            List<Account> userAccounts = accountRepository.findByOwner(user);
            List<String> syncedAccounts = new ArrayList<>();

            for (Account account : userAccounts) {
                if (!account.isSyncEnabled() || !SyncProvider.GALICIA.equals(account.getSyncProvider())) {
                    continue;
                }

                CommonResult syncResult;
                if (AccountType.BANK_ACCOUNT.equals(account.getType())) {
                    syncResult = accountManagementService.syncAccountMovements(account, cuentasCookies, true);
                } else if (AccountType.CREDIT_CARD.equals(account.getType())) {
                    syncResult = accountManagementService.syncCreditCardAccountMovements(account, skywalkerToken);
                } else {
                    continue;
                }

                if (syncResult.isError()) {
                    log.warn("[syncAllAccounts] Error al sincronizar cuenta {}: {}", account.getName(), syncResult.getMessage());
                } else {
                    syncedAccounts.add(account.getName());
                }
            }

            return CommonResult.ok(syncedAccounts, "Sincronizacion completada. Se sincronizaron " + syncedAccounts.size() + " cuentas");
        } catch (Exception e) {
            log.error("[syncAllAccounts] Error al sincronizar todas las cuentas", e);
            return CommonResult.error("Error al sincronizar: " + e.getMessage());
        }
    }

    @Override
    public CommonResult syncAllAccountsWithCookies(User user, String cookies) {
        try {
            String skywalkerToken = extractSkywalkerFromCookies(cookies);
            if (!StringUtils.hasText(skywalkerToken)) {
                return CommonResult.error("No se encontro el token Skywalker en las cookies provistas");
            }

            String cuentasCookies = (String) galiciaApiService.establishCuentasSession(cookies).getPayload();
            if (!StringUtils.hasText(cuentasCookies)) {
                return CommonResult.error("No se pudo establecer sesion en cuentas con las cookies provistas");
            }

            List<Account> userAccounts = accountRepository.findByOwner(user);
            List<String> syncedAccounts = new ArrayList<>();

            for (Account account : userAccounts) {
                if (!account.isSyncEnabled() || !SyncProvider.GALICIA.equals(account.getSyncProvider())) {
                    continue;
                }

                CommonResult syncResult;
                if (AccountType.BANK_ACCOUNT.equals(account.getType())) {
                    syncResult = accountManagementService.syncAccountMovements(account, cuentasCookies, true);
                } else if (AccountType.CREDIT_CARD.equals(account.getType())) {
                    syncResult = accountManagementService.syncCreditCardAccountMovements(account, skywalkerToken);
                } else {
                    continue;
                }

                if (syncResult.isError()) {
                    log.warn("[syncAllAccountsWithCookies] Error al sincronizar cuenta {}: {}", account.getName(), syncResult.getMessage());
                } else {
                    syncedAccounts.add(account.getName());
                }
            }

            return CommonResult.ok(syncedAccounts, "Sincronizacion completada. Se sincronizaron " + syncedAccounts.size() + " cuentas");
        } catch (Exception e) {
            log.error("[syncAllAccountsWithCookies] Error al sincronizar todas las cuentas con cookies", e);
            return CommonResult.error("Error al sincronizar: " + e.getMessage());
        }
    }

    private String extractSkywalkerFromCookies(String cookies) {
        if (!StringUtils.hasText(cookies)) return null;
        Pattern p = Pattern.compile("Skywalker\\s*=\\s*([^;]+)");
        Matcher m = p.matcher(cookies);
        return m.find() ? m.group(1).trim() : null;
    }
}