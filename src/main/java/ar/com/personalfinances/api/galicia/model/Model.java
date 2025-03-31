package ar.com.personalfinances.api.galicia.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Model implements Serializable {

    @JsonProperty("TotalPaginas")
    private Long totalPaginas;

    @JsonProperty("Movimientos")
    private List<Movimiento> movimientos;

    @JsonProperty("MovimientosPendientes")
    private List<Movimiento> movimientosPendientes;

    @JsonProperty("CantidadMovimientosRestantes")
    private Long cantidadMovimientosRestantes;

    @JsonProperty("CantidadMovimientosPendientesRestantes")
    private Long cantidadMovimientosPendientesRestantes;

    @JsonProperty("NumeroPagina")
    private Long numeroPagina;

    public Model() {
    }

    public Long getTotalPaginas() {
        return totalPaginas;
    }

    public void setTotalPaginas(Long totalPaginas) {
        this.totalPaginas = totalPaginas;
    }

    public List<Movimiento> getMovimientos() {
        return movimientos;
    }

    public void setMovimientos(List<Movimiento> movimientos) {
        this.movimientos = movimientos;
    }

    public List<Movimiento> getMovimientosPendientes() {
        return movimientosPendientes;
    }

    public void setMovimientosPendientes(List<Movimiento> movimientosPendientes) {
        this.movimientosPendientes = movimientosPendientes;
    }

    public Long getCantidadMovimientosRestantes() {
        return cantidadMovimientosRestantes;
    }

    public void setCantidadMovimientosRestantes(Long cantidadMovimientosRestantes) {
        this.cantidadMovimientosRestantes = cantidadMovimientosRestantes;
    }

    public Long getCantidadMovimientosPendientesRestantes() {
        return cantidadMovimientosPendientesRestantes;
    }

    public void setCantidadMovimientosPendientesRestantes(Long cantidadMovimientosPendientesRestantes) {
        this.cantidadMovimientosPendientesRestantes = cantidadMovimientosPendientesRestantes;
    }

    public Long getNumeroPagina() {
        return numeroPagina;
    }

    public void setNumeroPagina(Long numeroPagina) {
        this.numeroPagina = numeroPagina;
    }

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