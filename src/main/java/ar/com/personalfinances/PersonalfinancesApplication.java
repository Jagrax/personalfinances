package ar.com.personalfinances;

import ar.com.personalfinances.configuration.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@SpringBootApplication
@EntityScan(basePackages = {"ar.com.personalfinances"})
public class PersonalfinancesApplication extends SpringBootServletInitializer {

	@Autowired
	private ApplicationProperties appProperties;

	@Autowired
	private DataSource dataSource;

	public static void main(String[] args) {
		SpringApplication.run(PersonalfinancesApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		log.info("=======================================================");
		log.info("=======================================================");
		log.info("===      APLICACION INICIALIZADA CORRECTAMENTE      ===");
		log.info(centerInBanner(generateVersionString()));
		log.info("=======================================================");
		log.info("=======================================================");
	}

	private String generateVersionString() {
		try (Connection conn = dataSource.getConnection()) {
			String version = "v" + appProperties.getVersion();
			String dbUrl = conn.getMetaData().getURL();
			String dbName = dbUrl.contains("/") ? dbUrl.substring(dbUrl.lastIndexOf("/") + 1) : dbUrl;
			if (dbName.contains("?")) dbName = dbName.substring(0, dbName.indexOf("?"));
			String profile = appProperties.isLocalRuntime() ? "local" : "wildfly";
			return version + "/" + dbName + "/" + profile;
		} catch (Exception e) {
			return "v" + appProperties.getVersion() + "/DB:N/A/local";
		}
	}

	private String centerInBanner(String text) {
		int contentMax = 49;
		int padding = Math.max(1, (contentMax - text.length()) / 2);
		String left = " ".repeat(padding);
		String right = " ".repeat(Math.max(1, contentMax - text.length() - padding));
		return "===" + left + text + right + "===";
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(PersonalfinancesApplication.class);
	}
}
