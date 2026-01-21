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
public class Meta implements Serializable {

    @JsonProperty("method")
    private String method;

    @JsonProperty("operation")
    private String operation;

    @Override
    public String toString() {
        return "Meta [" +
                ((method != null) ? "method='" + method + "', " : "") +
                ((operation != null) ? "operation='" + operation + "', " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Meta meta = (Meta) o;
        return Objects.equals(method, meta.method) && Objects.equals(operation, meta.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, operation);
    }
}