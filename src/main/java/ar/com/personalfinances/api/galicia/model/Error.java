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

    @JsonProperty("reason")
    private String reason;
    
    @JsonProperty("login-tracking-id")
    private String loginTrackingId;
    
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("data")
    private String data;
    
    @JsonProperty("code_backend")
    private String codeBackend;
    
    @JsonProperty("code_internal")
    private String codeInternal;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("custom_message")
    private String customMessage;
    
    @JsonProperty("extensions")
    private String extensions;
    
    @JsonProperty("trace")
    private String trace;
    
    @JsonProperty("error_type")
    private String errorType;
    
    @JsonProperty("custom_title")
    private String customTitle;
    
    @JsonProperty("context")
    private String context;
    
    @JsonProperty("detail")
    private String detail;
    
    @JsonProperty("custom_description")
    private String customDescription;
    
    @JsonProperty("lang")
    private String lang;
    
    @JsonProperty("custom_error_type")
    private String customErrorType;
    
    @JsonProperty("custom_code")
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