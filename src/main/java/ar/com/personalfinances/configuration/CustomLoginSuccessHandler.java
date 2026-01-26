package ar.com.personalfinances.configuration;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.service.AccountManagementService;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.ApplicationMessageService;
import ar.com.personalfinances.util.ApplicationMessage;
import ar.com.personalfinances.util.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Configuration
public class CustomLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AlertEventService alertEventService;
    private final AccountRepository accountRepository;
    private final AccountManagementService accountManagementService;
    private final ApplicationMessageService applicationMessageService;

    @Autowired
    public CustomLoginSuccessHandler(AlertEventService alertEventService, AccountRepository accountRepository, AccountManagementService accountManagementService, ApplicationMessageService applicationMessageService) {
        this.alertEventService = alertEventService;
        this.accountRepository = accountRepository;
        this.accountManagementService = accountManagementService;
        this.applicationMessageService = applicationMessageService;
    }

    @Override
    protected void handle(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        if (response.isCommitted()) return;
        syncUserAccounts((User) authentication.getPrincipal(), request);
        String targetUrl = determineTargetUrl(authentication, request.getRemoteAddr());
        RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        redirectStrategy.sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(Authentication authentication, String remoteAddress) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority a : authorities) {
            roles.add(a.getAuthority());
        }

        User user = (User) authentication.getPrincipal();
        // Si tiene algun rol definido, lo mando al dashboard
        if (roles.contains("ADMIN") || roles.contains("USER")) {
            log.info("Se ha logueado el User[id={}, email={}]", user.getId(), user.getEmail());
            alertEventService.saveUserAlert(EntityEvent.LOGIN, user.getId(), remoteAddress, -1);
            return "/dashboard";
        } else {
            log.info("LOGIN - user.role.invalid: id = {}", user.getId());
        }

        // Si no tiene ningun rol valido, vuelve al log con mensaje de error
        return "/login?error=true&errorType=1";
    }

    private void syncUserAccounts(User user, HttpServletRequest request) {
        List<Account> userAccounts = accountRepository.findByOwner(user);
        for (Account userAccount : userAccounts) {
            if (userAccount.isSyncEnabled() && userAccount.getType().equals(AccountType.CREDIT_CARD)) {
                CommonResult syncResult = accountManagementService.syncCreditCardAccountMovements(userAccount);
                if (syncResult.isError()) {
                    applicationMessageService.add(request, ApplicationMessage.error("Error del Galicia al sincronizar la cuenta [" + userAccount.getId() + "|" + userAccount.getName() + "]: " + syncResult.getMessage()));
                } else if (syncResult.isWarning()) {
                    applicationMessageService.add(request, ApplicationMessage.error("Error del configuracion/validacion al sincronizar la cuenta [" + userAccount.getId() + "|" + userAccount.getName() + "]: " + syncResult.getMessage()));
                } else {
                    List<Expense> expensesSyncronized = (List<Expense>) syncResult.getPayload();
                    if (!CollectionUtils.isEmpty(expensesSyncronized)) {
                        applicationMessageService.add(request, ApplicationMessage.info("Hay " + expensesSyncronized.size() + " gasto(s) nuevo(s) en la tarjeta " + userAccount.getName()));
                    }
                }
            }
        }
    }
}