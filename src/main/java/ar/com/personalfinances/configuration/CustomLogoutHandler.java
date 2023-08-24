package ar.com.personalfinances.configuration;

import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.AlertEventService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;

@Service
public class CustomLogoutHandler implements LogoutHandler {

    private final AlertEventService alertEventService;

    @Autowired
    public CustomLogoutHandler(AlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        alertEventService.saveUserAlert(EntityEvent.LOGOUT, ((User) authentication.getPrincipal()).getId(), request.getRemoteAddr(), -1);
    }
}