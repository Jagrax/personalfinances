package ar.com.personalfinances.configuration;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.ApplicationMessageService;
import ar.com.personalfinances.service.MenuService;
import ar.com.personalfinances.util.ApplicationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final MenuService menuService;
    private final ApplicationMessageService applicationMessageService;
    private final String appVersion;

    public GlobalControllerAdvice(MenuService menuService, ApplicationMessageService applicationMessageService, @Value("${app.version:local}") String appVersion) {
        this.menuService = menuService;
        this.applicationMessageService = applicationMessageService;
        this.appVersion = resolveAppVersion(appVersion);
    }

    @ModelAttribute
    public void handleRequest(HttpServletRequest httpServletRequest, Model model) {
        model.addAttribute("httpServletRequest", httpServletRequest);
        model.addAttribute("appVersion", appVersion);
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

    private String resolveAppVersion(String appVersion) {
        if (appVersion != null && !appVersion.contains("project.version")) {
            return appVersion;
        }

        Path pomPath = Path.of("pom.xml");
        if (!Files.exists(pomPath)) {
            return "local";
        }

        try {
            String pom = Files.readString(pomPath);
            Matcher matcher = Pattern.compile("<artifactId>personalfinances</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException ignored) {
            return "local";
        }

        return "local";
    }
}
