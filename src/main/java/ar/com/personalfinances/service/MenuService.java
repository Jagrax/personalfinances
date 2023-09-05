package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.util.Menu;
import ar.com.personalfinances.util.MenuItem;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

@Service
public class MenuService {

    final AccountRepository accountRepository;

    public MenuService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @CacheEvict(value = "userMenuCache", allEntries = true)
    public Menu getMenu(User user) {
        List<MenuItem> menuItems = new ArrayList<>();
        if (user != null) {
            menuItems.add(new MenuItem(null, "speedometer2", "Dashboard", "/dashboard", null));

            List<MenuItem> expensesSubMenu = new ArrayList<>();
            expensesSubMenu.add(new MenuItem(null, "list-columns-reverse", "Unificados", "/expenses", null));
            List<Account> userAccounts = accountRepository.findByOwner(user);
            if (!CollectionUtils.isEmpty(userAccounts)) {
                Map<AccountType, List<Account>> accountsByType = userAccounts.stream().sorted(Comparator.comparing(Account::getType).thenComparing(Account::getName)).collect(groupingBy(Account::getType));
                for (AccountType type : accountsByType.keySet()) {
                    expensesSubMenu.add(new MenuItem(null, null, resolveAccountTypeLabel(type), null, null));
                    for (Account account : accountsByType.get(type)) {
                        expensesSubMenu.add(new MenuItem(null, null, account.getName(), "/expenses?accountType=" + type.name() + "&accountName=" + account.getName(), null));
                    }
                }
            }
            menuItems.add(new MenuItem("reportes", "cash-coin", "Ingresos/Egresos", "#", expensesSubMenu));

            List<MenuItem> administracionSubMenu = new ArrayList<>();
            administracionSubMenu.add(new MenuItem(null, "bank2", "Mis cuentas", "/accounts", null));
            if (user.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ADMIN"))) {
                administracionSubMenu.add(new MenuItem(null, null, "Categorias", "/categories", null));
            }
            menuItems.add(new MenuItem("administracion", "gear", "Administración", "#", administracionSubMenu));
        }

        return new Menu(menuItems);
    }

    private String resolveAccountTypeLabel(AccountType accountType) {
        switch (accountType) {
            case CREDIT_CARD:     {return "Tarjetas de crédito";}
            case VIRTUAL_ACCOUNT: {return "Cuentas Virtuales";}
            case BANK_ACCOUNT:    {return "Cuentas Bancarias";}
            default:              {return "";}
        }
    }
}