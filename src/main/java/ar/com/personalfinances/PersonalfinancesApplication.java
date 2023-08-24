package ar.com.personalfinances;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@Slf4j
@SpringBootApplication
public class PersonalfinancesApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(PersonalfinancesApplication.class, args);
		log.info("=======================================================");
		log.info("=======================================================");
		log.info("===      APLICACION INICIALIZADA CORRECTAMENTE      ===");
		log.info("=======================================================");
		log.info("=======================================================");
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(PersonalfinancesApplication.class);
	}
}
