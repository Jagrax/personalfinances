package ar.com.personalfinances.api.galicia.model;

import ar.com.personalfinances.util.NumberUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Movimiento implements Serializable {

    @JsonProperty("ID")
    private String id;

    @JsonProperty("Icono")
    private Integer icono;

    @JsonProperty("IconoLabel")
    private String iconoLabel;

    @JsonProperty("Fecha")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy", timezone = "GMT-03:00")
    private Date fecha;

    @JsonProperty("DescripcionAMostrar")
    private String descripcionAMostrar;

    @JsonProperty("DescripcionSide")
    private String descripcionSide;

    @JsonProperty("Moneda")
    private Integer moneda;

    @JsonProperty("ImporteCredito")
    private BigDecimal importeCredito;

    @JsonProperty("ImporteDebito")
    private BigDecimal importeDebito;

    @JsonProperty("ImporteDebitoLabel")
    private String importeDebitoLabel;

    @JsonProperty("ImporteCreditoLabel")
    private String importeCreditoLabel;

    @JsonProperty("SaldoParcial")
    private BigDecimal saldoParcial;

    @JsonProperty("SaldoParcialLabel")
    private String saldoParcialLabel;

    @JsonProperty("Comentario")
    private String comentario;

    @JsonProperty("IndiceMovimiento")
    private Long indiceMovimiento;

    @JsonProperty("EsMovimientoPendiente")
    private Boolean esMovimientoPendiente;

    public Movimiento() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getIcono() {
        return icono;
    }

    public void setIcono(Integer icono) {
        this.icono = icono;
    }

    public String getIconoLabel() {
        return iconoLabel;
    }

    public void setIconoLabel(String iconoLabel) {
        this.iconoLabel = iconoLabel;
    }

    public Date getFecha() {
        return fecha;
    }

    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }

    public String getDescripcionAMostrar() {
        return descripcionAMostrar;
    }

    public void setDescripcionAMostrar(String descripcionAMostrar) {
        this.descripcionAMostrar = descripcionAMostrar;
    }

    public String getDescripcionSide() {
        return descripcionSide;
    }

    public void setDescripcionSide(String descripcionSide) {
        this.descripcionSide = descripcionSide;
    }

    public Integer getMoneda() {
        return moneda;
    }

    public void setMoneda(Integer moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getImporteCredito() {
        return importeCredito;
    }

    public void setImporteCredito(BigDecimal importeCredito) {
        this.importeCredito = importeCredito;
    }

    public void setImporteCredito(String importeCredito) {
        this.importeCredito = NumberUtils.parseBigDecimal(importeCredito);
    }

    public BigDecimal getImporteDebito() {
        return importeDebito;
    }

    public void setImporteDebito(BigDecimal importeDebito) {
        this.importeDebito = importeDebito;
    }

    public void setImporteDebito(String importeDebito) {
        this.importeDebito = NumberUtils.parseBigDecimal(importeDebito);
    }

    public String getImporteDebitoLabel() {
        return importeDebitoLabel;
    }

    public void setImporteDebitoLabel(String importeDebitoLabel) {
        this.importeDebitoLabel = importeDebitoLabel;
    }

    public String getImporteCreditoLabel() {
        return importeCreditoLabel;
    }

    public void setImporteCreditoLabel(String importeCreditoLabel) {
        this.importeCreditoLabel = importeCreditoLabel;
    }

    public BigDecimal getSaldoParcial() {
        return saldoParcial;
    }

    public void setSaldoParcial(BigDecimal saldoParcial) {
        this.saldoParcial = saldoParcial;
    }

    public void setSaldoParcial(String saldoParcial) {
        this.saldoParcial = NumberUtils.parseBigDecimal(saldoParcial);
    }

    public String getSaldoParcialLabel() {
        return saldoParcialLabel;
    }

    public void setSaldoParcialLabel(String saldoParcialLabel) {
        this.saldoParcialLabel = saldoParcialLabel;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public Long getIndiceMovimiento() {
        return indiceMovimiento;
    }

    public void setIndiceMovimiento(Long indiceMovimiento) {
        this.indiceMovimiento = indiceMovimiento;
    }

    public Boolean getEsMovimientoPendiente() {
        return esMovimientoPendiente;
    }

    public void setEsMovimientoPendiente(Boolean esMovimientoPendiente) {
        this.esMovimientoPendiente = esMovimientoPendiente;
    }

    @Override
    public String toString() {
        return "Movimiento [" +
                ((id != null) ? "id='" + id + "', " : "") +
                ((icono != null) ? "icono=" + icono + ", " : "") +
                ((iconoLabel != null) ? "iconoLabel='" + iconoLabel + "', " : "") +
                ((fecha != null) ? "fecha=" + fecha + ", " : "") +
                ((descripcionAMostrar != null) ? "descripcionAMostrar='" + descripcionAMostrar + "', " : "") +
                ((descripcionSide != null) ? "descripcionSide='" + descripcionSide + "', " : "") +
                ((moneda != null) ? "moneda=" + moneda + ", " : "") +
                ((importeCredito != null) ? "importeCredito=" + importeCredito + ", " : "") +
                ((importeDebito != null) ? "importeDebito=" + importeDebito + ", " : "") +
                ((importeDebitoLabel != null) ? "importeDebitoLabel='" + importeDebitoLabel + "', " : "") +
                ((importeCreditoLabel != null) ? "importeCreditoLabel='" + importeCreditoLabel + "', " : "") +
                ((saldoParcial != null) ? "saldoParcial=" + saldoParcial + ", " : "") +
                ((saldoParcialLabel != null) ? "saldoParcialLabel='" + saldoParcialLabel + "', " : "") +
                ((comentario != null) ? "comentario='" + comentario + "', " : "") +
                ((indiceMovimiento != null) ? "indiceMovimiento=" + indiceMovimiento + ", " : "") +
                ((esMovimientoPendiente != null) ? "esMovimientoPendiente=" + esMovimientoPendiente + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Movimiento that = (Movimiento) o;
        return Objects.equals(id, that.id) && Objects.equals(icono, that.icono) && Objects.equals(iconoLabel, that.iconoLabel) && Objects.equals(fecha, that.fecha) && Objects.equals(descripcionAMostrar, that.descripcionAMostrar) && Objects.equals(descripcionSide, that.descripcionSide) && Objects.equals(moneda, that.moneda) && Objects.equals(importeCredito, that.importeCredito) && Objects.equals(importeDebito, that.importeDebito) && Objects.equals(importeDebitoLabel, that.importeDebitoLabel) && Objects.equals(importeCreditoLabel, that.importeCreditoLabel) && Objects.equals(saldoParcial, that.saldoParcial) && Objects.equals(saldoParcialLabel, that.saldoParcialLabel) && Objects.equals(comentario, that.comentario) && Objects.equals(indiceMovimiento, that.indiceMovimiento) && Objects.equals(esMovimientoPendiente, that.esMovimientoPendiente);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, icono, iconoLabel, fecha, descripcionAMostrar, descripcionSide, moneda, importeCredito, importeDebito, importeDebitoLabel, importeCreditoLabel, saldoParcial, saldoParcialLabel, comentario, indiceMovimiento, esMovimientoPendiente);
    }
}