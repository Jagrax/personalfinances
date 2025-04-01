package ar.com.personalfinances.api.galicia.io;

import ar.com.personalfinances.api.galicia.model.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMovimientosTarjetaResponse implements Serializable {

    @JsonProperty("errores")
    private List<String> errores;

    @JsonProperty("data")
    private Data data;

    @Override
    public String toString() {
        return "GetMovimientosTarjetaResponse [" +
                ((errores != null) ? "errores=" + Arrays.toString(errores.toArray()) + ", " : "") +
                ((data != null) ? "data=" + data + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetMovimientosTarjetaResponse that = (GetMovimientosTarjetaResponse) o;
        return Objects.equals(errores, that.errores) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errores, data);
    }
}