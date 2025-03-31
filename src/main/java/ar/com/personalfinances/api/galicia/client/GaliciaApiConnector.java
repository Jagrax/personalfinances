package ar.com.personalfinances.api.galicia.client;

import ar.com.personalfinances.api.galicia.io.GetMovimientosCuentaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class GaliciaApiConnector {

    final String baseUrl;

    public enum TipoMovimiento {
        TODOS("Todos"),
        INGRESOS("Ingresos"),
        EGRESOS("Egresos");

        private final String value;

        TipoMovimiento(String value) {
            this.value = value;
        }
    }

    public GaliciaApiConnector(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public GetMovimientosCuentaResponse getMovimientosCuenta(String cookie, Date fechaDesde, Date fechaHasta, TipoMovimiento tipoMovimiento, Long pageNumber) {
        final String path = "/Cuentas/GetMovimientosCuenta";
        // Configurar HttpClient con el resolvedor del sistema
        final HttpClient httpClient = HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE);

        final WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .defaultHeader(HttpHeaders.HOST, "cuentas.bancogalicia.com.ar")
                .defaultHeader(HttpHeaders.COOKIE, cookie)
                .build();

        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (fechaDesde != null) formData.add("fd", sdf.format(fechaDesde));
        if (fechaHasta != null) formData.add("fh", sdf.format(fechaHasta));
        if (tipoMovimiento != null) formData.add("motivo", tipoMovimiento.value);
        if (pageNumber != null) formData.add("pagina", String.valueOf(pageNumber));

        log.debug("[getMovimientosCuenta] Request POST por obtener movimientos de la cuenta");
        return webClient.post()
                .uri("/Cuentas/GetMovimientosCuenta")
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .toEntity(String.class)
                .flatMap(entity -> {
                    final MediaType responseContentType = entity.getHeaders().getContentType();
                    if (responseContentType != null && responseContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                        try {
                            return Mono.just(new ObjectMapper().readValue(entity.getBody(), GetMovimientosCuentaResponse.class));
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    } else {
                        return Mono.error(new IllegalStateException("Sesión expirada o acceso no autorizado"));
                    }
                })
                .block();
    }
}