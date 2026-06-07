package ar.com.personalfinances.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final CustomLoginSuccessHandler successHandler;
    private final CustomLogoutHandler customLogoutHandler;

    public SecurityConfiguration(CustomLoginSuccessHandler successHandler, CustomLogoutHandler customLogoutHandler) {
        this.successHandler = successHandler;
        this.customLogoutHandler = customLogoutHandler;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
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
                        .logoutUrl("/logout")
                        .addLogoutHandler(customLogoutHandler)
                        .logoutSuccessUrl("/")
                )
                .authenticationProvider(authenticationProvider(userDetailsService))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .build();
    }
}