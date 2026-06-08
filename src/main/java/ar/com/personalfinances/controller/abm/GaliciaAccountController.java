package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.GaliciaSyncService;
import ar.com.personalfinances.util.ApplicationUtils;
import ar.com.personalfinances.service.ApplicationMessageService;
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
    public String discoverAccounts(@RequestParam(value = "token", required = false) String skywalkerToken,
                                   @RequestParam(value = "cookies", required = false) String galiciaCookies,
                                   Model model, HttpServletRequest request) {
        User user = ApplicationUtils.getUserFromSession();

        if (galiciaCookies != null && !galiciaCookies.isBlank()) {
            CommonResult result = galiciaSyncService.discoverAccountsWithCookies(user, galiciaCookies.trim());
            if (result.isError()) {
                applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error(result.getMessage()));
                return "redirect:/galicia-connect";
            }
            model.addAttribute("discoveryResult", result.getPayload());
            model.addAttribute("galiciaCookies", galiciaCookies.trim());
            return "galicia-connect";
        }

        if (skywalkerToken == null || skywalkerToken.isBlank()) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error("Token o cookies son requeridos"));
            return "redirect:/galicia-connect";
        }

        CommonResult result = galiciaSyncService.discoverAccounts(skywalkerToken.trim());
        if (result.isError()) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error(result.getMessage()));
            return "redirect:/galicia-connect";
        }

        model.addAttribute("discoveryResult", result.getPayload());
        model.addAttribute("skywalkerToken", skywalkerToken.trim());
        return "galicia-connect";
    }

    @PostMapping("/import-accounts")
    public String importAccounts(@RequestParam("accountsJson") String accountsJson, HttpServletRequest request) {
        User user = ApplicationUtils.getUserFromSession();
        if (user == null) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error("Usuario no autenticado"));
            return "redirect:/login";
        }

        CommonResult result = galiciaSyncService.importSelectedAccounts(user, accountsJson);
        if (result.isError()) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error(result.getMessage()));
        } else {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.success(result.getMessage()));
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/sync")
    public String syncAllAccounts(@RequestParam(value = "galiciaToken", required = false) String skywalkerToken,
                                  @RequestParam(value = "galiciaCookies", required = false) String galiciaCookies,
                                  HttpServletRequest request) {
        User user = ApplicationUtils.getUserFromSession();
        if (user == null) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error("Usuario no autenticado"));
            return "redirect:/login";
        }

        if (galiciaCookies != null && !galiciaCookies.isBlank()) {
            CommonResult result = galiciaSyncService.syncAllAccountsWithCookies(user, galiciaCookies.trim());
            if (result.isError()) {
                applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error(result.getMessage()));
            } else {
                applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.success(result.getMessage()));
            }
            return "redirect:/expenses";
        }

        if (skywalkerToken == null || skywalkerToken.isBlank()) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error("Token o cookies son requeridos"));
            return "redirect:/dashboard";
        }

        CommonResult result = galiciaSyncService.syncAllAccounts(user, skywalkerToken.trim());
        if (result.isError()) {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.error(result.getMessage()));
        } else {
            applicationMessageService.add(request, ar.com.personalfinances.util.ApplicationMessage.success(result.getMessage()));
        }

        return "redirect:/expenses";
    }
}