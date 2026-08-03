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

/** Atualiza o valor total dos CTEs e o rateio por PESOBRUTO das NFes relacionadas. */
@Job(serviceName = "TFVAtualizaCteNfeSP", frequency = "&300000", transactionType = EJBTransactionType.Supports)
public class AtualizaCteNfeJob extends IJob {
    private static final Logger LOG = Logger.getLogger(AtualizaCteNfeJob.class.getName());

    @Override
    public void onSchedule() {
        JapeSession.SessionHandle session = null;
        try {
            session = JapeSession.open();
            JdbcWrapper jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper();

            NativeSql master = new NativeSql(jdbc);
            master.appendSql("MERGE INTO TFV_CTE DEST USING (");
            master.appendSql("SELECT IXN.CHAVEACESSO CHAVE_CTE, MAX(TO_NUMBER(X.VALOR_CTE, '999999999999990D00', 'NLS_NUMERIC_CHARACTERS=.,')) VALOR_TOTAL, MAX(IXN.DHEMISS) DT_EMISSAO, MAX(CASE WHEN IXN.NUNOTA IS NULL THEN 'PENDENTE' ELSE 'LANCADO' END) STATUS ");
            master.appendSql("FROM TGFIXN IXN CROSS JOIN XMLTABLE('/cteProc/CTe/infCte' PASSING XMLTYPE(IXN.XML) COLUMNS VALOR_CTE VARCHAR2(60) PATH 'vPrest/vTPrest') X ");
            master.appendSql("WHERE IXN.TIPOCTE IS NOT NULL AND IXN.DHEMISS >= DATE '2026-07-01' AND IXN.CHAVEACESSO IS NOT NULL GROUP BY IXN.CHAVEACESSO) ORIGEM ");
            master.appendSql("ON (DEST.TFV_CHAVE_CTE = ORIGEM.CHAVE_CTE) WHEN MATCHED THEN UPDATE SET DEST.TFV_VALOR_TOTAL=ORIGEM.VALOR_TOTAL, DEST.TFV_DT_EMISSAO=ORIGEM.DT_EMISSAO, DEST.TFV_STATUS=ORIGEM.STATUS ");
            master.appendSql("WHEN NOT MATCHED THEN INSERT (TFV_CHAVE_CTE,TFV_VALOR_TOTAL,TFV_DT_EMISSAO,TFV_STATUS) VALUES (ORIGEM.CHAVE_CTE,ORIGEM.VALOR_TOTAL,ORIGEM.DT_EMISSAO,ORIGEM.STATUS)");
            master.executeUpdate();

            NativeSql clear = new NativeSql(jdbc);
            clear.appendSql("DELETE FROM TFV_CTE_NFE_RATEIO WHERE TFV_CHAVE_CTE IN (SELECT TFV_CHAVE_CTE FROM TFV_CTE WHERE TFV_DT_EMISSAO >= DATE '2026-07-01')");
            clear.executeUpdate();

            NativeSql allocation = new NativeSql(jdbc);
            allocation.appendSql("INSERT INTO TFV_CTE_NFE_RATEIO (TFV_CHAVE_CTE,TFV_CHAVE_NFE,TFV_PESO_NFE,TFV_PESO_TOTAL,TFV_VALOR_RATEADO) ");
            allocation.appendSql("SELECT R.CHAVE_CTE, R.CHAVE_NFE, R.PESO_NFE, R.PESO_TOTAL, CASE WHEN R.PESO_TOTAL > 0 THEN C.TFV_VALOR_TOTAL * R.PESO_NFE / R.PESO_TOTAL ELSE C.TFV_VALOR_TOTAL / R.QTD_NFE END ");
            allocation.appendSql("FROM (SELECT B.CHAVE_CTE, B.CHAVE_NFE, B.PESO_NFE, SUM(B.PESO_NFE) OVER (PARTITION BY B.CHAVE_CTE) PESO_TOTAL, COUNT(*) OVER (PARTITION BY B.CHAVE_CTE) QTD_NFE FROM (SELECT IXN.CHAVEACESSO CHAVE_CTE, XNF.CHAVE_NFE, MAX(NVL(CAB.PESOBRUTO,0)) PESO_NFE ");
            allocation.appendSql("FROM TGFIXN IXN CROSS JOIN XMLTABLE('/cteProc/CTe/infCte/infCTeNorm/infDoc/infNFe' PASSING XMLTYPE(IXN.XML) COLUMNS CHAVE_NFE VARCHAR2(44) PATH 'chave') XNF ");
            allocation.appendSql("INNER JOIN TGFCAB CAB ON TRIM(CAB.CHAVENFE) = TRIM(XNF.CHAVE_NFE) WHERE XNF.CHAVE_NFE IS NOT NULL AND IXN.TIPOCTE IS NOT NULL AND IXN.DHEMISS >= DATE '2026-07-01' AND IXN.CHAVEACESSO IS NOT NULL GROUP BY IXN.CHAVEACESSO, XNF.CHAVE_NFE) B) R ");
            allocation.appendSql("JOIN TFV_CTE C ON C.TFV_CHAVE_CTE = R.CHAVE_CTE");
            allocation.executeUpdate();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao atualizar o rateio de CTE por NFe", e);
        } finally {
            if (session != null) JapeSession.close(session);
        }
    }
}
