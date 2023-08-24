package ar.com.personalfinances.configuration;

import ar.com.personalfinances.repository.UserRepository;
import ar.com.personalfinances.service.UserServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
public class SecurityConfiguration {

    private final CustomLoginSuccessHandler successHandler;
    private final CustomLogoutHandler customLogoutHandler;

    private final UserRepository userRepository;

    public SecurityConfiguration(CustomLoginSuccessHandler successHandler, CustomLogoutHandler customLogoutHandler, UserRepository userRepository) {
        this.successHandler = successHandler;
        this.customLogoutHandler = customLogoutHandler;
        this.userRepository = userRepository;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserServiceImpl(passwordEncoder(), userRepository);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.authorizeRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/login"), new AntPathRequestMatcher("/register")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/css/**"), new AntPathRequestMatcher("/js/**"), new AntPathRequestMatcher("/images/**")).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .failureUrl("/login?error=true&errorType=0")
                        .successHandler(successHandler)
                        .usernameParameter("email")
                        .passwordParameter("password")
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .addLogoutHandler(customLogoutHandler)
                        .logoutSuccessUrl("/")
                )
                .authenticationProvider(authenticationProvider())
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .build();
    }
}