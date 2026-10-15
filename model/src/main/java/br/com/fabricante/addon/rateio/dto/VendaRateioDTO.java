package br.com.fabricante.addon.rateio.dto;

import java.math.BigDecimal;

/** Dados de uma nota de venda participante do rateio. */
public class VendaRateioDTO {
    private BigDecimal nunota;
    private BigDecimal valorRateio;
    private BigDecimal percentual;
    private String tipoCarregamento;

    public VendaRateioDTO() {
    }

    public BigDecimal getNunota() {
        return nunota;
    }

    public void setNunota(BigDecimal nunota) {
        this.nunota = nunota;
    }

    public BigDecimal getValorRateio() {
        return valorRateio;
    }

    public void setValorRateio(BigDecimal valorRateio) {
        this.valorRateio = valorRateio;
    }

    public BigDecimal getPercentual() {
        return percentual;
    }

    public void setPercentual(BigDecimal percentual) {
        this.percentual = percentual;
    }

    public String getTipoCarregamento() {
        return tipoCarregamento;
    }

    public void setTipoCarregamento(String tipoCarregamento) {
        this.tipoCarregamento = tipoCarregamento;
    }
}
