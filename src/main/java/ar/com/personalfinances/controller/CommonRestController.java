package ar.com.personalfinances.controller;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest")
public class CommonRestController {

    private final UserRepository userRepository;

    public CommonRestController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/user/random")
    public String getRandomUser() {
        long qty = userRepository.count();
        int idx = (int) (Math.random() * qty);
        Page<User> userPage = userRepository.findAll(PageRequest.of(idx, 1));
        if (userPage.hasContent()) {
            return userPage.getContent().get(0).toString();
        } else {
            return "No user found in DB!";
        }
    }
}