package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Role;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final UserRepository userRepository;

    public UserServiceImpl(BCryptPasswordEncoder bCryptPasswordEncoder, UserRepository userRepository) {
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.userRepository = userRepository;
    }

    @Override
    public User saveUser(User user) {
        String encodedPassword = bCryptPasswordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    @Override
    public List<Object> isUserPresent(User user) {
        boolean userExists = false;
        String message = null;
        Optional<User> existingUserEmail = userRepository.findByEmail(user.getEmail());
        if (existingUserEmail.isPresent()) {
            userExists = true;
            message = "Email Already Present!";
        }
        Optional<User> existingUserMobile = userRepository.findByMobile(user.getMobile());
        if (existingUserMobile.isPresent()) {
            userExists = true;
            message = "Mobile Number Already Present!";
        }
        if (existingUserEmail.isPresent() && existingUserMobile.isPresent()) {
            message = "Email and Mobile Number Both Already Present!";
        }
        log.debug("existingUserEmail.isPresent() - " + existingUserEmail.isPresent() + " | existingUserMobile.isPresent() - " + existingUserMobile.isPresent());
        return Arrays.asList(userExists, message);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException(email));
    }
}