package ar.com.personalfinances.configuration;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.ApplicationMessageService;
import ar.com.personalfinances.service.MenuService;
import ar.com.personalfinances.util.ApplicationMessage;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final MenuService menuService;
    private final ApplicationMessageService applicationMessageService;

    public GlobalControllerAdvice(MenuService menuService, ApplicationMessageService applicationMessageService) {
        this.menuService = menuService;
        this.applicationMessageService = applicationMessageService;
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

        List<ApplicationMessage> msg = applicationMessageService.consume(httpServletRequest);
        if (msg != null) {
            model.addAttribute("applicationMessages", msg);
        }
    }
}