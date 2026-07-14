package ar.com.personalfinances.service;

import ar.com.personalfinances.web.model.ScanItemDTO;
import ar.com.personalfinances.web.model.ScanTicketResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ScanService {

    @Value("${app.ai.gemini.api-keys}")
    private String apiKeys;

    private final AtomicInteger keyIndex = new AtomicInteger(0);

    @Value("${app.ai.gemini.model:gemini-3-flash-preview}")
    private String model;

    @Value("${app.ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp", "image/bmp", "application/pdf");

    public ScanTicketResponse scanTicket(MultipartFile file) {
        log.info("Iniciando scan: archivo={}, size={}, type={}", file.getOriginalFilename(), file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Formato no soportado. Usá JPG, PNG, WebP, BMP o PDF");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen es demasiado grande (máx 10MB)");
        }

        log.info("Scan: archivo validado, codificando a base64...");
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
            log.info("Scan: base64 generado ({} bytes)", base64.length());
        } catch (Exception e) {
            throw new RuntimeException("Error al leer el archivo", e);
        }

        String[] keys = apiKeys.split(",");
        log.info("Scan: {} API keys disponibles, rotando...", keys.length);
        RuntimeException lastError = null;

        for (int attempt = 0; attempt < keys.length; attempt++) {
            String key = keys[Math.floorMod(keyIndex.getAndIncrement(), keys.length)].trim();
            String keySuffix = key.substring(Math.max(0, key.length() - 4));
            log.info("Scan: intento {}/{} con key ...{}", attempt + 1, keys.length, keySuffix);
            try {
                return callGemini(base64, contentType, key);
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Gemini 429 con key ...{}: {}", keySuffix, e.getMessage());
                lastError = new RuntimeException("Se alcanzó el límite de requests a Gemini. La cuota se resetea a medianoche (Pacific Time).");
            } catch (HttpClientErrorException.NotFound e) {
                log.warn("Gemini 404: model not found", e);
                throw new RuntimeException("El modelo de Gemini configurado no está disponible. Revisá app.ai.gemini.model en application-wildfly.properties.");
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                log.warn("Gemini 503 con key ...{}: {}", keySuffix, e.getMessage());
                lastError = new RuntimeException("Gemini está temporalmente sobrecargado. Intentá de nuevo en unos segundos.");
            } catch (Exception e) {
                log.error("Error scanning ticket", e);
                throw new RuntimeException("Error al procesar el ticket: " + e.getMessage(), e);
            }
        }

        log.warn("Scan: agotadas todas las API keys sin éxito");
        throw lastError != null ? lastError : new RuntimeException("No se pudo escanear el ticket con ninguna API key.");
    }

    private ScanTicketResponse callGemini(String base64, String contentType, String apiKey) {
        log.info("Gemini: preparando request...");
        String url = baseUrl + "/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mimeType", contentType);
        inlineData.put("data", base64);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("inlineData", inlineData);

        Map<String, Object> textPart = new HashMap<>();
        String prompt = "Extraé el total y todos los importes de esta factura o ticket: items (productos/servicios) e impuestos (IVA, IIBB, percepciones, etc.). "
                + "Respondé SOLO un objeto JSON sin explicaciones ni markdown. "
                + "{ total: number, items: [{ description: string, amount: number }] }. "
                + "Incluí TODOS los conceptos en items (incluyendo impuestos) para que la suma de los amounts coincida con el total.";
        textPart.put("text", prompt);

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(textPart);
        parts.add(imagePart);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", Collections.singletonList(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("Gemini: enviando request a {}...", model);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, JsonNode.class
        );
        log.info("Gemini: respuesta recibida (status={})", response.getStatusCode());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Error al comunicarse con Gemini: " + response.getStatusCode());
        }

        String text = response.getBody()
                .path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
        log.info("Gemini: texto crudo recibido ({} chars)", text != null ? text.length() : 0);

        if (text == null || text.isBlank()) {
            log.warn("Gemini returned empty response");
            return new ScanTicketResponse(BigDecimal.ZERO, Collections.emptyList());
        }

        text = text.trim();
        if (text.startsWith("```")) {
            log.info("Gemini: limpiando bloque markdown...");
            text = text.replaceAll("```(?:json)?\\s*", "").trim();
        }

        log.info("Gemini: parseando JSON...");

        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("Gemini returned invalid JSON: {}", text);
            return new ScanTicketResponse(BigDecimal.ZERO, Collections.emptyList());
        }
        BigDecimal total = root.has("total") ? BigDecimal.valueOf(root.get("total").asDouble()) : BigDecimal.ZERO;
        List<ScanItemDTO> items = new ArrayList<>();
        if (root.has("items") && root.get("items").isArray()) {
            for (JsonNode itemNode : root.get("items")) {
                String desc = itemNode.has("description") ? itemNode.get("description").asText() : "";
                BigDecimal amt = itemNode.has("amount") ? BigDecimal.valueOf(itemNode.get("amount").asDouble()) : BigDecimal.ZERO;
                items.add(new ScanItemDTO(desc, amt));
            }
        }

        log.info("Scanned ticket: total={}, items={}", total, items.size());
        return new ScanTicketResponse(total, items);
    }

}
