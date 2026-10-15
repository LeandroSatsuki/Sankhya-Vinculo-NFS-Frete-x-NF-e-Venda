package br.com.fabricante.addon.rateio.dto;

import java.math.BigDecimal;
import java.util.List;

/** Payload da conclusão transacional do rateio de frete. */
public class RateioFreteRequest {
    private BigDecimal nunotaFrete;
    private List<VendaRateioDTO> vendas;

    public RateioFreteRequest() {
    }

    public BigDecimal getNunotaFrete() {
        return nunotaFrete;
    }

    public void setNunotaFrete(BigDecimal nunotaFrete) {
        this.nunotaFrete = nunotaFrete;
    }

    public List<VendaRateioDTO> getVendas() {
        return vendas;
    }

    public void setVendas(List<VendaRateioDTO> vendas) {
        this.vendas = vendas;
    }
}
