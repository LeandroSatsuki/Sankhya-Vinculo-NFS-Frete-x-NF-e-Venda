package br.com.fabricante.addon.rateio;

import br.com.fabricante.addon.rateio.dto.ClassificacaoLogisticaRequest;
import br.com.fabricante.addon.rateio.dto.RateioFreteRequest;
import br.com.fabricante.addon.rateio.dto.VendaRateioDTO;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.sdk.data.sql.NativeSqlBuilder;
import br.com.sankhya.studio.annotations.Service;
import br.com.sankhya.studio.annotations.enums.EJBTransactionType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Conclui o rateio e persiste a classificação logística na mesma transação.
 * A classificação é independente do vínculo: sua chave é somente a NUNOTA da venda.
 */
@Service(serviceName = "TFVRateioFreteSP", transactionType = EJBTransactionType.Required)
public class RateioFreteController {
    private static final Logger LOG = Logger.getLogger(RateioFreteController.class.getName());
    private static final String TIPO_NORMAL = "N";
    private static final String TIPO_INDUSTRIA = "I";

    private static final String MERGE_VINCULO =
        "MERGE INTO TFV_VINCULO_FRETE_VENDA DEST " +
        "USING (SELECT :NUNOTA_FRETE NUNOTA_FRETE, :NUNOTA_VENDA NUNOTA_VENDA, " +
        ":VALOR_RATEIO VALOR_RATEIO, :PERCENTUAL PERCENTUAL FROM DUAL) ORIGEM " +
        "ON (DEST.TFV_NUNOTA_FRETE = ORIGEM.NUNOTA_FRETE " +
        "AND DEST.TFV_NUNOTA_VENDA = ORIGEM.NUNOTA_VENDA) " +
        "WHEN MATCHED THEN UPDATE SET DEST.TFV_VLR_RATEIO = ORIGEM.VALOR_RATEIO, " +
        "DEST.TFV_PERCENTUAL = ORIGEM.PERCENTUAL, DEST.TFV_ATIVO = 'S', " +
        "DEST.TFV_ORIGEM = 'RATEIO_PESO' " +
        "WHEN NOT MATCHED THEN INSERT (TFV_NUNOTA_FRETE, TFV_NUNOTA_VENDA, " +
        "TFV_VLR_RATEIO, TFV_PERCENTUAL, TFV_ATIVO, TFV_ORIGEM) " +
        "VALUES (ORIGEM.NUNOTA_FRETE, ORIGEM.NUNOTA_VENDA, ORIGEM.VALOR_RATEIO, " +
        "ORIGEM.PERCENTUAL, 'S', 'RATEIO_PESO')";

    private static final String MERGE_CLASSIFICACAO =
        "MERGE INTO TFV_CLASSIFICACAO_LOGISTICA DEST " +
        "USING (SELECT :NUNOTA NUNOTA, :TIPO TIPO, :CODUSU CODUSU FROM DUAL) ORIGEM " +
        "ON (DEST.TFV_NUNOTA = ORIGEM.NUNOTA) " +
        "WHEN MATCHED THEN UPDATE SET DEST.TFV_TIPO_CARREGAMENTO = ORIGEM.TIPO, " +
        "DEST.TFV_CODUSU = ORIGEM.CODUSU, DEST.TFV_DHALTER = SYSDATE " +
        "WHEN NOT MATCHED THEN INSERT (TFV_NUNOTA, TFV_TIPO_CARREGAMENTO, " +
        "TFV_CODUSU, TFV_DHALTER) VALUES (ORIGEM.NUNOTA, ORIGEM.TIPO, " +
        "ORIGEM.CODUSU, SYSDATE)";

    public Map<String, Object> salvarRateio(RateioFreteRequest rateio) {
        try {
            validarRateio(rateio);
            BigDecimal codUsu = usuarioAtual();
            salvarVinculos(rateio);
            salvarClassificacoes(rateio.getVendas(), codUsu);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("quantidadeVendas", rateio.getVendas().size());
            response.put("mensagem", "Rateio e classificação logística gravados com sucesso.");
            return response;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao concluir rateio e classificação logística", e);
            throw new IllegalStateException(
                "Não foi possível concluir o rateio. Nenhuma alteração foi gravada. Detalhe: " + mensagemRaiz(e), e);
        }
    }

    public Map<String, Object> salvarClassificacoes(ClassificacaoLogisticaRequest request) {
        try {
            List<VendaRateioDTO> vendas = request == null ? null : request.getVendas();
            validarClassificacoes(vendas);
            salvarClassificacoes(vendas, usuarioAtual());

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("quantidadeVendas", vendas.size());
            response.put("mensagem", "Classificação logística atualizada com sucesso.");
            return response;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao salvar classificação logística", e);
            throw new IllegalStateException(
                "Não foi possível salvar a classificação logística. Nenhuma alteração foi gravada. Detalhe: " +
                    mensagemRaiz(e), e);
        }
    }

    private void validarRateio(RateioFreteRequest rateio) throws Exception {
        if (rateio == null || numeroPositivo(rateio.getNunotaFrete()) == null) {
            throw new IllegalArgumentException("Informe a NUNOTA da nota de frete.");
        }
        if (!notaExiste(rateio.getNunotaFrete())) {
            throw new IllegalArgumentException("A nota de frete informada não existe.");
        }
        validarClassificacoes(rateio.getVendas());
        for (VendaRateioDTO venda : rateio.getVendas()) {
            if (venda.getValorRateio() == null || venda.getValorRateio().signum() < 0) {
                throw new IllegalArgumentException("O valor rateado deve ser maior ou igual a zero.");
            }
            if (venda.getPercentual() == null || venda.getPercentual().signum() < 0) {
                throw new IllegalArgumentException("O percentual do rateio deve ser maior ou igual a zero.");
            }
        }
    }

    private void validarClassificacoes(List<VendaRateioDTO> vendas) throws Exception {
        if (vendas == null || vendas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma nota de venda.");
        }
        Set<BigDecimal> nunotas = new HashSet<>();
        for (VendaRateioDTO venda : vendas) {
            BigDecimal nunota = venda == null ? null : numeroPositivo(venda.getNunota());
            if (nunota == null) {
                throw new IllegalArgumentException("Foi informada uma NUNOTA de venda inválida.");
            }
            nunota = nunota.stripTrailingZeros();
            if (!nunotas.add(nunota)) {
                throw new IllegalArgumentException("A nota " + nunota.toPlainString() + " foi informada mais de uma vez.");
            }
            String tipo = normalizarTipo(venda.getTipoCarregamento());
            venda.setTipoCarregamento(tipo);
            if (!notaExiste(nunota)) {
                throw new IllegalArgumentException("A nota de venda " + nunota.toPlainString() + " não existe.");
            }
        }
    }

    private void salvarVinculos(final RateioFreteRequest rateio) throws Exception {
        NativeSqlBuilder.BatchUpdateBuilder<VendaRateioDTO> batch = NativeSqlBuilder.batchUpdate();
        batch.sql(MERGE_VINCULO)
            .params(rateio.getVendas())
            .mapper((binder, venda) -> binder
                .set("NUNOTA_FRETE", rateio.getNunotaFrete())
                .set("NUNOTA_VENDA", venda.getNunota())
                .set("VALOR_RATEIO", venda.getValorRateio())
                .set("PERCENTUAL", venda.getPercentual()))
            .execute(100);
    }

    private void salvarClassificacoes(List<VendaRateioDTO> vendas, final BigDecimal codUsu) throws Exception {
        NativeSqlBuilder.BatchUpdateBuilder<VendaRateioDTO> batch = NativeSqlBuilder.batchUpdate();
        batch.sql(MERGE_CLASSIFICACAO)
            .params(vendas)
            .mapper((binder, venda) -> binder
                .set("NUNOTA", venda.getNunota())
                .set("TIPO", venda.getTipoCarregamento())
                .set("CODUSU", codUsu))
            .execute(100);
    }

    private boolean notaExiste(BigDecimal nunota) throws Exception {
        Integer quantidade = NativeSqlBuilder.builder()
            .sql("SELECT COUNT(1) FROM TGFCAB WHERE NUNOTA = :NUNOTA")
            .param("NUNOTA", nunota)
            .resultAsInt();
        return quantidade != null && quantidade > 0;
    }

    private BigDecimal usuarioAtual() {
        AuthenticationInfo authenticationInfo = AuthenticationInfo.getCurrent();
        if (authenticationInfo == null || authenticationInfo.getUserID() == null) {
            throw new IllegalStateException("Não foi possível identificar o usuário logado.");
        }
        return authenticationInfo.getUserID();
    }

    private String normalizarTipo(String tipo) {
        String valor = tipo == null ? TIPO_NORMAL : tipo.trim().toUpperCase();
        if (!TIPO_NORMAL.equals(valor) && !TIPO_INDUSTRIA.equals(valor)) {
            throw new IllegalArgumentException("Tipo de carregamento inválido. Utilize N ou I.");
        }
        return valor;
    }

    private BigDecimal numeroPositivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0 ? valor : null;
    }

    private String mensagemRaiz(Throwable error) {
        Throwable atual = error;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
        }
        String mensagem = atual.getMessage();
        return mensagem == null || mensagem.trim().isEmpty() ? atual.getClass().getSimpleName() : mensagem;
    }
}
