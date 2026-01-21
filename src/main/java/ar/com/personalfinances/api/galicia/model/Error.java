package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Error implements Serializable {

    @JsonProperty("reason")// "No se enviaron fechas para la consulta del payments-manager",
    private String reason;
    
    @JsonProperty("login-tracking-id")// null,
    private String loginTrackingId;
    
    @JsonProperty("code")// null,
    private String code;
    
    @JsonProperty("data")// null,
    private String data;
    
    @JsonProperty("code_backend")// null,
    private String codeBackend;
    
    @JsonProperty("code_internal")// null,
    private String codeInternal;
    
    @JsonProperty("description")// null,
    private String description;
    
    @JsonProperty("message")// null,
    private String message;
    
    @JsonProperty("title")// null,
    private String title;
    
    @JsonProperty("custom_message")// null,
    private String customMessage;
    
    @JsonProperty("extensions")// null,
    private String extensions;
    
    @JsonProperty("trace")// null,
    private String trace;
    
    @JsonProperty("error_type")// null,
    private String errorType;
    
    @JsonProperty("custom_title")// null,
    private String customTitle;
    
    @JsonProperty("context")// null,
    private String context;
    
    @JsonProperty("detail")// null,
    private String detail;
    
    @JsonProperty("custom_description")// null,
    private String customDescription;
    
    @JsonProperty("lang")// null,
    private String lang;
    
    @JsonProperty("custom_error_type")// null,
    private String customErrorType;
    
    @JsonProperty("custom_code")// null
    private String customCode;

    @Override
    public String toString() {
        return "Error [" +
                ((reason != null) ? "reason='" + reason + "', " : "") +
                ((loginTrackingId != null) ? "loginTrackingId='" + loginTrackingId + "', " : "") +
                ((code != null) ? "code='" + code + "', " : "") +
                ((data != null) ? "data='" + data + "', " : "") +
                ((codeBackend != null) ? "codeBackend='" + codeBackend + "', " : "") +
                ((codeInternal != null) ? "codeInternal='" + codeInternal + "', " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((message != null) ? "message='" + message + "', " : "") +
                ((title != null) ? "title='" + title + "', " : "") +
                ((customMessage != null) ? "customMessage='" + customMessage + "', " : "") +
                ((extensions != null) ? "extensions='" + extensions + "', " : "") +
                ((trace != null) ? "trace='" + trace + "', " : "") +
                ((errorType != null) ? "errorType='" + errorType + "', " : "") +
                ((customTitle != null) ? "customTitle='" + customTitle + "', " : "") +
                ((context != null) ? "context='" + context + "', " : "") +
                ((detail != null) ? "detail='" + detail + "', " : "") +
                ((customDescription != null) ? "customDescription='" + customDescription + "', " : "") +
                ((lang != null) ? "lang='" + lang + "', " : "") +
                ((customErrorType != null) ? "customErrorType='" + customErrorType + "', " : "") +
                ((customCode != null) ? "customCode='" + customCode + "', " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Error error = (Error) o;
        return Objects.equals(reason, error.reason) && Objects.equals(loginTrackingId, error.loginTrackingId) && Objects.equals(code, error.code) && Objects.equals(data, error.data) && Objects.equals(codeBackend, error.codeBackend) && Objects.equals(codeInternal, error.codeInternal) && Objects.equals(description, error.description) && Objects.equals(message, error.message) && Objects.equals(title, error.title) && Objects.equals(customMessage, error.customMessage) && Objects.equals(extensions, error.extensions) && Objects.equals(trace, error.trace) && Objects.equals(errorType, error.errorType) && Objects.equals(customTitle, error.customTitle) && Objects.equals(context, error.context) && Objects.equals(detail, error.detail) && Objects.equals(customDescription, error.customDescription) && Objects.equals(lang, error.lang) && Objects.equals(customErrorType, error.customErrorType) && Objects.equals(customCode, error.customCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, loginTrackingId, code, data, codeBackend, codeInternal, description, message, title, customMessage, extensions, trace, errorType, customTitle, context, detail, customDescription, lang, customErrorType, customCode);
    }
}