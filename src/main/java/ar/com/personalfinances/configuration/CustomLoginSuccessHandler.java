package ar.com.personalfinances.configuration;

import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.AlertEventService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Configuration
public class CustomLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AlertEventService alertEventService;

    @Autowired
    public CustomLoginSuccessHandler(AlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    @Override
    protected void handle(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String targetUrl = determineTargetUrl(authentication, request.getRemoteAddr());
        if (response.isCommitted()) return;
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
            log.info("Se ha logueado el User[id=" + user.getId() + ", email=" + user.getEmail() + "]");
            alertEventService.saveUserAlert(EntityEvent.LOGIN, user.getId(), remoteAddress, -1);
            return "/dashboard";
        } else {
            log.info("LOGIN - user.role.invalid: id = " + user.getId());
        }

        // Si no tiene ningun rol valido, vuelve al log con mensaje de error
        return "/login?error=true&errorType=1";
    }
}