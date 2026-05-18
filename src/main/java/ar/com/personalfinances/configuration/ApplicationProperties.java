package ar.com.personalfinances.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ApplicationProperties {

    /**
     * Se configura desde la VM Options de IntelliJ en la Run Configuration
     */
    @Value("${app.local:false}")
    private boolean localRuntime;
}