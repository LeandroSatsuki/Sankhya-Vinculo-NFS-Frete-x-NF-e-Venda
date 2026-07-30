package br.com.fabricante.addon.jobs;

import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.studio.annotations.Job;
import br.com.sankhya.studio.annotations.enums.EJBTransactionType;
import br.com.sankhya.studio.stereotypes.IJob;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Mantém o índice leve de CTEs por chave da NFe. */
@Job(serviceName = "TFVAtualizaCteNfeSP", frequency = "&300000", transactionType = EJBTransactionType.Supports)
public class AtualizaCteNfeJob extends IJob {
    private static final Logger LOG = Logger.getLogger(AtualizaCteNfeJob.class.getName());

    @Override
    public void onSchedule() {
        JapeSession.SessionHandle session = null;
        try {
            session = JapeSession.open();
            JdbcWrapper jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper();
            NativeSql sql = new NativeSql(jdbc);
            sql.appendSql("MERGE INTO TFV_CTE_NFE DEST ");
            sql.appendSql("USING (SELECT XNF.CHAVE_NFE, IXN.CHAVEACESSO CHAVE_CTE, ");
            sql.appendSql("TO_NUMBER(XCTE.VALOR_CTE, '999999999999990D00', 'NLS_NUMERIC_CHARACTERS=.,') VALOR_CTE, ");
            sql.appendSql("IXN.DHEMISS DT_EMISSAO, CASE WHEN IXN.NUNOTA IS NULL THEN 'PENDENTE' ELSE 'LANCADO' END STATUS ");
            sql.appendSql("FROM TGFIXN IXN ");
            sql.appendSql("CROSS JOIN XMLTABLE('/cteProc/CTe/infCte' PASSING XMLTYPE(IXN.XML) COLUMNS VALOR_CTE VARCHAR2(60) PATH 'vPrest/vTPrest') XCTE ");
            sql.appendSql("CROSS JOIN XMLTABLE('/cteProc/CTe/infCte/infCTeNorm/infDoc/infNFe' PASSING XMLTYPE(IXN.XML) COLUMNS CHAVE_NFE VARCHAR2(44) PATH 'chave') XNF ");
            sql.appendSql("WHERE IXN.TIPOCTE IS NOT NULL AND IXN.DHEMISS >= DATE '2026-07-01' AND XNF.CHAVE_NFE IS NOT NULL) ORIGEM ");
            sql.appendSql("ON (DEST.TFV_CHAVE_NFE = ORIGEM.CHAVE_NFE) ");
            sql.appendSql("WHEN MATCHED THEN UPDATE SET DEST.TFV_CHAVE_CTE=ORIGEM.CHAVE_CTE, DEST.TFV_VALOR_CTE=ORIGEM.VALOR_CTE, DEST.TFV_DT_EMISSAO=ORIGEM.DT_EMISSAO, DEST.TFV_STATUS=ORIGEM.STATUS ");
            sql.appendSql("WHEN NOT MATCHED THEN INSERT (TFV_CHAVE_NFE,TFV_CHAVE_CTE,TFV_VALOR_CTE,TFV_DT_EMISSAO,TFV_STATUS) VALUES (ORIGEM.CHAVE_NFE,ORIGEM.CHAVE_CTE,ORIGEM.VALOR_CTE,ORIGEM.DT_EMISSAO,ORIGEM.STATUS)");
            sql.executeUpdate();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao atualizar o índice TFV_CTE_NFE", e);
        } finally {
            if (session != null) JapeSession.close(session);
        }
    }
}
