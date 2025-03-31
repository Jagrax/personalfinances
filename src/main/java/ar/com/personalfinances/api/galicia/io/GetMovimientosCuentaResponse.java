package ar.com.personalfinances.api.galicia.io;

import ar.com.personalfinances.api.galicia.model.Model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMovimientosCuentaResponse implements Serializable {

    @JsonProperty("IsError")
    private Boolean isError;

    @JsonProperty("Model")
    private Model model;

    public GetMovimientosCuentaResponse() {
    }

    public Boolean getError() {
        return isError;
    }

    public void setError(Boolean error) {
        isError = error;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    @Override
    public String toString() {
        return "GetMovimientosCuentaResponse [" +
                ((isError != null) ? "isError=" + isError + ", " : "") +
                ((model != null) ? "model=" + model + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetMovimientosCuentaResponse that = (GetMovimientosCuentaResponse) o;
        return Objects.equals(isError, that.isError) && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isError, model);
    }
}