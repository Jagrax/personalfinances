package ar.com.personalfinances.api.galicia.io;

import ar.com.personalfinances.api.galicia.model.DataTc;
import ar.com.personalfinances.api.galicia.model.Error;
import ar.com.personalfinances.api.galicia.model.Meta;
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
public class PostCardsMovementsResponse implements Serializable {

    @JsonProperty("errors")
    private List<Error> errors;

    @JsonProperty("data")
    private List<DataTc> data;

    @JsonProperty("meta")
    private Meta meta;

    @Override
    public String toString() {
        return "PostCardsMovementsResponse [" +
                ((errors != null) ? "errores=" + Arrays.toString(errors.toArray()) + ", " : "") +
                ((data != null) ? "data=" + data + ", " : "") +
                ((meta != null) ? "meta=" + meta + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostCardsMovementsResponse that = (PostCardsMovementsResponse) o;
        return Objects.equals(errors, that.errors) && Objects.equals(data, that.data) && Objects.equals(meta, that.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errors, data, meta);
    }
}