package ar.com.personalfinances.configuration;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.MenuService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final MenuService menuService;

    public GlobalControllerAdvice(MenuService menuService) {
        this.menuService = menuService;
    }

    @ModelAttribute
    public void handleRequest(HttpServletRequest httpServletRequest, Model model) {
        model.addAttribute("httpServletRequest", httpServletRequest);
        User user = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User) {
            user = (User) authentication.getPrincipal();
        }

        model.addAttribute("menu", menuService.getMenu(user));
    }
}