package ar.com.personalfinances.controller;

import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.UserService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

@Controller
public class AuthController {

    private final UserService userService;
    private final AlertEventService alertEventService;

    public AuthController(UserService userService, AlertEventService alertEventService) {
        this.userService = userService;
        this.alertEventService = alertEventService;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login() {
        return "auth/login";
    }

    @RequestMapping(value = {"/register"}, method = RequestMethod.GET)
    public String register(Model model) {
        model.addAttribute("user", new User());
        return "auth/register";
    }

    @RequestMapping(value = {"/register"}, method = RequestMethod.POST)
    public String registerUser(Model model, @Valid User user, BindingResult bindingResult, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("successMessage", "Hay datos que no están bien");
            model.addAttribute("bindingResult", bindingResult);
            return "auth/register";
        }
        List<Object> userPresentObj = userService.isUserPresent(user);
        if ((Boolean) userPresentObj.get(0)) {
            model.addAttribute("successMessage", userPresentObj.get(1));
            return "auth/register";
        }

        user = userService.saveUser(user);
        alertEventService.saveUserAlert(EntityEvent.CREATED, user.getId(), request.getRemoteAddr(), -1);
        model.addAttribute("successMessage", "User registered successfully!");

        return "auth/login";
    }
}