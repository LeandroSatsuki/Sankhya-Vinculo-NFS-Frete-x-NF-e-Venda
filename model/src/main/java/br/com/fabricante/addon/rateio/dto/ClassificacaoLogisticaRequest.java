package br.com.fabricante.addon.rateio.dto;

import java.util.List;

/** Payload para alterações de classificação em vínculos já concluídos. */
public class ClassificacaoLogisticaRequest {
    private List<VendaRateioDTO> vendas;

    public ClassificacaoLogisticaRequest() {
    }

    public List<VendaRateioDTO> getVendas() {
        return vendas;
    }

    public void setVendas(List<VendaRateioDTO> vendas) {
        this.vendas = vendas;
    }
}
