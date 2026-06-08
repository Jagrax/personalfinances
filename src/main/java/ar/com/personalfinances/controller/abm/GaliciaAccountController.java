package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.ApplicationMessageService;
import ar.com.personalfinances.service.GaliciaSyncService;
import ar.com.personalfinances.util.ApplicationMessage;
import ar.com.personalfinances.util.ApplicationUtils;
import ar.com.personalfinances.util.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Controller
@RequestMapping("/galicia")
public class GaliciaAccountController {

    private final GaliciaSyncService galiciaSyncService;
    private final ApplicationMessageService applicationMessageService;

    public GaliciaAccountController(GaliciaSyncService galiciaSyncService, ApplicationMessageService applicationMessageService) {
        this.galiciaSyncService = galiciaSyncService;
        this.applicationMessageService = applicationMessageService;
    }

    @PostMapping("/discover")
    public String discoverAccounts(@RequestParam("cookies") String galiciaCookies,
                                   Model model, HttpServletRequest request) {
        User user = ApplicationUtils.getUserFromSession();

        CommonResult result = galiciaSyncService.discoverAccounts(user, galiciaCookies.trim());
        if (result.isError()) {
            applicationMessageService.add(request, ApplicationMessage.error(result.getMessage()));
            return "redirect:/galicia-connect";
        }
        model.addAttribute("discoveryResult", result.getPayload());
        model.addAttribute("galiciaCookies", galiciaCookies.trim());
        return "galicia-connect";
    }

    @PostMapping("/import-accounts")
    public String importAccounts(@RequestParam("accountsJson") String accountsJson, HttpServletRequest request) {
        User user = ApplicationUtils.getUserFromSession();
        if (user == null) {
            applicationMessageService.add(request, ApplicationMessage.error("Usuario no autenticado"));
            return "redirect:/login";
        }

        CommonResult result = galiciaSyncService.importSelectedAccounts(user, accountsJson);
        if (result.isError()) {
            applicationMessageService.add(request, ApplicationMessage.error(result.getMessage()));
        } else {
            applicationMessageService.add(request, ApplicationMessage.success(result.getMessage()));
        }

        return "redirect:/accounts";
    }

    @PostMapping("/sync")
    public String syncAllAccounts(@RequestParam("galiciaCookies") String galiciaCookies,
                                  HttpServletRequest request) {
        User user = ApplicationUtils.getUserFromSession();
        if (user == null) {
            applicationMessageService.add(request, ApplicationMessage.error("Usuario no autenticado"));
            return "redirect:/login";
        }

        CommonResult result = galiciaSyncService.syncAllAccounts(user, galiciaCookies.trim());
        if (result.isError()) {
            applicationMessageService.add(request, ApplicationMessage.error(result.getMessage()));
        } else {
            applicationMessageService.add(request, ApplicationMessage.success(result.getMessage()));
        }

        return "redirect:/accounts";
    }
}
