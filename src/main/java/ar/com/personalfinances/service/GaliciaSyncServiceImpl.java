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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
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
    public CommonResult discoverAccounts(User user, String cookies) {
        try {
            CommonResult cardsResult = galiciaApiService.getCardsOverview(cookies);

            // Try SSO first, then direct session, then original cookies as last resort
            CommonResult cuentasSessionResult = galiciaApiService.establishCuentasSession(cookies);
            String cuentasCookies;
            if (cuentasSessionResult.isError()) {
                log.warn("[discoverAccounts] establishCuentasSession (SSO) failed, trying direct: {}", cuentasSessionResult.getMessage());
                cuentasSessionResult = galiciaApiService.establishCuentasSessionDirect(cookies);
                if (cuentasSessionResult.isError()) {
                    log.warn("[discoverAccounts] establishCuentasSessionDirect also failed, falling back to original cookies: {}", cuentasSessionResult.getMessage());
                    cuentasCookies = cookies;
                } else {
                    cuentasCookies = (String) cuentasSessionResult.getPayload();
                }
            } else {
                cuentasCookies = (String) cuentasSessionResult.getPayload();
            }

            CommonResult cuentasPageResult = galiciaApiService.getCuentasInicioPage(cuentasCookies);
            List<Map<String, String>> bankAccounts = new ArrayList<>();
            if (!cuentasPageResult.isError()) {
                String html = (String) cuentasPageResult.getPayload();
                Document doc = Jsoup.parse(html);
                Elements boxes = doc.select(".box_ctas");
                log.trace("[discoverAccounts] Found {} .box_ctas elements in cuentas page, bodySnippet=[{}]", boxes.size(),
                    html.substring(0, Math.min(500, html.length())));
                for (Element box : boxes) {
                    Map<String, String> account = new LinkedHashMap<>();
                    account.put("id", box.attr("data-indice"));
                    account.put("type", box.attr("data-tipo"));
                    account.put("currencyLabel", box.select(".orangeSquare").text());
                    account.put("name", box.select("p.clickeable.hidden-xs").text());
                    account.put("balance", box.select("h4").text());
                    account.put("accountNumber", box.select("h3 span").text());
                    bankAccounts.add(account);
                }
            } else {
                log.warn("[discoverAccounts] getCuentasInicioPage failed: {}", cuentasPageResult.getMessage());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("onlinebankingCookies", cookies);
            result.put("cardsRawJson", cardsResult.isError() ? null : cardsResult.getPayload());
            result.put("bankAccounts", bankAccounts);

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
            log.error("[discoverAccounts] Error al descubrir cuentas con cookies", e);
            return CommonResult.error("Error al descubrir cuentas: " + e.getMessage());
        }
    }

    private Account findOrCreateAccount(User user, String name, AccountType type, String currency, String externalAccountId) {
        List<Account> existing = accountRepository.findByOwnerAndType(user, type);
        for (Account a : existing) {
            if (name.equals(a.getName())) {
                a.setExternalAccountId(externalAccountId);
                a.setSyncProvider(SyncProvider.GALICIA);
                a.setSyncEnabled(true);
                if (currency != null && AccountType.BANK_ACCOUNT.equals(type)) {
                    a.setCurrency(currency);
                }
                return accountRepository.save(a);
            }
        }
        Account account = new Account();
        account.setOwner(user);
        account.setName(name);
        account.setType(type);
        account.setSubtype(AccountSubtype.BANCO_GALICIA.name());
        account.setSyncProvider(SyncProvider.GALICIA);
        account.setExternalAccountId(externalAccountId);
        account.setSyncEnabled(true);
        if (currency != null && AccountType.BANK_ACCOUNT.equals(type)) {
            account.setCurrency(currency);
        }
        return accountRepository.save(account);
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
                    String accountName = card.has("name") && !card.get("name").isNull() ? card.get("name").asText() : null;
                    if (!StringUtils.hasText(accountName)) {
                        if ("VISA".equalsIgnoreCase(brand)) {
                            accountName = "VISA";
                        } else if ("MASTERCARD".equalsIgnoreCase(brand) || "MASTER".equalsIgnoreCase(brand)) {
                            accountName = "Master Card";
                        } else {
                            accountName = brand + (lastFour != null ? " (" + lastFour + ")" : "");
                        }
                    }

                    Account account = findOrCreateAccount(user, accountName, AccountType.CREDIT_CARD, null, accountNumber);
                    processedAccounts.add(account);
                    log.info("[importSelectedAccounts] Tarjeta de credito procesada: {} con externalAccountId {} (id={})", accountName, accountNumber, account.getId());
                }
            }

            if (root.has("bankAccounts") && root.get("bankAccounts").isArray()) {
                for (JsonNode bankAccount : root.get("bankAccounts")) {
                    String accountIndex = bankAccount.has("id") ? bankAccount.get("id").asText() : null;
                    String accountTipo = bankAccount.has("type") ? bankAccount.get("type").asText() : null;
                    String externalAccountId = (accountIndex != null && accountTipo != null) ? accountIndex + "|" + accountTipo : accountIndex;

                    if (bankAccount.has("existingAccountId") && !bankAccount.get("existingAccountId").isNull()) {
                        Long existingId = bankAccount.get("existingAccountId").asLong();
                        Optional<Account> opt = accountRepository.findById(existingId);
                        if (opt.isPresent()) {
                            Account existing = opt.get();
                            existing.setExternalAccountId(externalAccountId);
                            existing.setSyncProvider(SyncProvider.GALICIA);
                            existing.setSyncEnabled(true);
                            accountRepository.save(existing);
                            processedAccounts.add(existing);
                            log.info("[importSelectedAccounts] Cuenta bancaria asociada a cuenta existente {} (id={})", existing.getName(), existingId);
                        }
                        continue;
                    }

                    String accountName = bankAccount.has("customName") && !bankAccount.get("customName").isNull() ? bankAccount.get("customName").asText() : null;
                    if (!StringUtils.hasText(accountName)) {
                        accountName = bankAccount.has("name") ? bankAccount.get("name").asText() : "Galicia Bank Account";
                    }
                    String currency = bankAccount.has("currency") ? bankAccount.get("currency").asText() : "ARS";

                    Account account = findOrCreateAccount(user, accountName, AccountType.BANK_ACCOUNT, currency, externalAccountId);
                    processedAccounts.add(account);
                    log.info("[importSelectedAccounts] Cuenta bancaria procesada: {} con externalAccountId {} (id={})", accountName, externalAccountId, account.getId());
                }
            }

            return CommonResult.ok(processedAccounts, "Se procesaron " + processedAccounts.size() + " cuentas");
        } catch (Exception e) {
            log.error("[importSelectedAccounts] Error al importar cuentas", e);
            return CommonResult.error("Error al importar cuentas: " + e.getMessage());
        }
    }

    @Override
    public CommonResult syncAllAccounts(User user, String cookies) {
        try {
            List<Account> userAccounts = accountRepository.findByOwner(user);
            List<String> syncedAccounts = new ArrayList<>();

            for (Account account : userAccounts) {
                if (!account.isSyncEnabled() || !SyncProvider.GALICIA.equals(account.getSyncProvider())) {
                    continue;
                }

                CommonResult syncResult;
                if (AccountType.BANK_ACCOUNT.equals(account.getType())) {
                    syncResult = accountManagementService.syncAccountMovements(account, cookies);
                } else if (AccountType.CREDIT_CARD.equals(account.getType())) {
                    syncResult = accountManagementService.syncCreditCardAccountMovements(account, cookies);
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
}
