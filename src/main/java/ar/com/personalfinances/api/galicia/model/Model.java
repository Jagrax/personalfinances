package ar.com.personalfinances.api.galicia.model;

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
public class Model implements Serializable {

    @JsonProperty("TotalPaginas")
    private Long totalPaginas;

    @JsonProperty("Movimientos")
    private List<BankAccountMovement> movimientos;

    @JsonProperty("MovimientosPendientes")
    private List<BankAccountMovement> movimientosPendientes;

    @JsonProperty("CantidadMovimientosRestantes")
    private Long cantidadMovimientosRestantes;

    @JsonProperty("CantidadMovimientosPendientesRestantes")
    private Long cantidadMovimientosPendientesRestantes;

    @JsonProperty("NumeroPagina")
    private Long numeroPagina;

    @Override
    public String toString() {
        return "Model [" +
                ((totalPaginas != null) ? "totalPaginas=" + totalPaginas + ", " : "") +
                ((movimientos != null) ? "movimientos=" + Arrays.toString(movimientos.toArray()) + ", " : "") +
                ((movimientosPendientes != null) ? "movimientosPendientes=" + Arrays.toString(movimientosPendientes.toArray()) + ", " : "") +
                ((cantidadMovimientosRestantes != null) ? "cantidadMovimientosRestantes=" + cantidadMovimientosRestantes + ", " : "") +
                ((cantidadMovimientosPendientesRestantes != null) ? "cantidadMovimientosPendientesRestantes=" + cantidadMovimientosPendientesRestantes + ", " : "") +
                ((numeroPagina != null) ? "numeroPagina=" + numeroPagina + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Model model = (Model) o;
        return Objects.equals(totalPaginas, model.totalPaginas) && Objects.equals(movimientos, model.movimientos) && Objects.equals(movimientosPendientes, model.movimientosPendientes) && Objects.equals(cantidadMovimientosRestantes, model.cantidadMovimientosRestantes) && Objects.equals(cantidadMovimientosPendientesRestantes, model.cantidadMovimientosPendientesRestantes) && Objects.equals(numeroPagina, model.numeroPagina);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalPaginas, movimientos, movimientosPendientes, cantidadMovimientosRestantes, cantidadMovimientosPendientesRestantes, numeroPagina);
    }
}