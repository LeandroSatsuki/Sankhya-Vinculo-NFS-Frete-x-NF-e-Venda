package br.com.telemassas.logistica.rateio;

import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.auth.MGEAuthorizationManager;
import br.com.sankhya.modelcore.comercial.ComercialUtils;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.studio.annotations.Service;
import br.com.sankhya.studio.annotations.enums.EJBTransactionType;
import br.com.telemassas.logistica.rateio.dto.ConsultaVinculoRequest;
import br.com.telemassas.logistica.rateio.dto.InativarVinculoRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.*;

/** Manual TFV links only. Does not write fiscal documents, pools or automatic CT-e parcels. */
@Service(serviceName="LGCManutencaoVinculoSP", transactionType=EJBTransactionType.Required)
public class ManutencaoVinculoController {
    private static final String RESOURCE="addon-logistica.FreteVendaVinculoPOC";
    private static final String LINK_SELECT =
        "SELECT V.TFV_NUNOTA_FRETE CUSTO, V.TFV_NUNOTA_VENDA NOTA, V.TFV_VLR_RATEIO VALOR, " +
        "V.TFV_PERCENTUAL PERCENTUAL, V.TFV_ATIVO ATIVO, V.TFV_ORIGEM ORIGEM, V.TFV_OBSERVACAO OBSERVACAO, " +
        "F.NUNOTA CABECALHO, F.NUMNOTA NF_CUSTO, F.VLRNOTA TOTAL_CUSTO, " +
        ContabilizacaoCustoSql.estado("V.TFV_NUNOTA_FRETE", "F.NUNOTA") + " CONTABILIZADO, " +
        "N.NUMNOTA NF, N.CODEMP EMPRESA, N.SERIENOTA SERIE, TO_CHAR(N.DTNEG,'DD/MM/YYYY') DATA, P.NOMEPARC CLIENTE " +
        "FROM TFV_VINCULO_FRETE_VENDA V LEFT JOIN TGFCAB F ON F.NUNOTA=V.TFV_NUNOTA_FRETE " +
        "LEFT JOIN TGFCAB N ON N.NUNOTA=V.TFV_NUNOTA_VENDA LEFT JOIN TGFPAR P ON P.CODPARC=N.CODPARC ";

    private AuthenticationInfo acesso(int permission) throws Exception {
        AuthenticationInfo auth=AuthenticationInfo.getCurrent();
        if(auth==null || auth.getUserID()==null) throw new SecurityException("Sessão não autenticada.");
        final String action;
        switch(permission) {
            case MGEAuthorizationManager.CONSULTAR: action=MGEAuthorizationManager.SIGLA_CONSULTAR; break;
            case MGEAuthorizationManager.ALTERAR: action=MGEAuthorizationManager.SIGLA_ALTERAR; break;
            case MGEAuthorizationManager.CONFIGURAR: action=MGEAuthorizationManager.SIGLA_CONFIGURAR; break;
            default: throw new SecurityException("Operação de acesso não reconhecida.");
        }
        if (!MGEAuthorizationManager.getMGEResourceAuthorization(RESOURCE,auth.getUserID()).hasAuthorization(action))
            throw new SecurityException("Usuário sem autorização para esta operação no recurso de vínculos.");
        return auth;
    }
    private boolean autorizado(int permission) {
        try { acesso(permission); return true; } catch(Exception e) { return false; }
    }
    private NativeSql sql(JdbcWrapper jdbc,String text) throws Exception {
        NativeSql s=new NativeSql(jdbc);s.appendSql(text);return s;
    }
    private List<Map<String,Object>> rows(NativeSql s) throws Exception {
        List<Map<String,Object>> result=new ArrayList<>();
        try(ResultSet rs=s.executeQuery()) {
            int count=rs.getMetaData().getColumnCount();
            while(rs.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=count;i++) row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),rs.getObject(i));
                result.add(row);
            }
        } finally { NativeSql.releaseResources(s); }
        return result;
    }
    private String version(Map<String,Object> row) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(String field:Arrays.asList("custo","nota","valor","percentual","ativo","origem","observacao","cabecalho","nf_custo","total_custo","contabilizado")) {
            Object raw=row.get(field);
            String value=raw==null?"<null>":raw instanceof BigDecimal?((BigDecimal)raw).stripTrailingZeros().toPlainString():raw.toString();
            digest.update((value.length()+":"+value+";").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));return hex.toString();
    }

    private Map<String,Object> estadoCusto(JdbcWrapper jdbc, BigDecimal custo) throws Exception {
        NativeSql query=sql(jdbc,"SELECT F.NUNOTA CABECALHO, " +
            ContabilizacaoCustoSql.estado("C.CUSTO","F.NUNOTA") +
            " CONTABILIZADO FROM (SELECT :CUSTO CUSTO FROM DUAL) C LEFT JOIN TGFCAB F ON F.NUNOTA=C.CUSTO");
        query.setNamedParameter("CUSTO",custo);
        List<Map<String,Object>> found=rows(query);
        if(found.size()!=1)throw new IllegalStateException("Não foi possível comprovar o estado do custo.");
        Map<String,Object> state=found.get(0);
        state.put("baixado",ComercialUtils.notaTemFinanceiroBaixado(custo,jdbc));
        return state;
    }

    /** Shared source for the contextual screen; errors never become 'not posted/settled'. */
    public Map<String,Object> consultarEstadoCusto(ConsultaVinculoRequest filtro) throws Exception {
        JapeSession.SessionHandle h=JapeSession.open();
        try {
            acesso(MGEAuthorizationManager.CONSULTAR);
            if(filtro==null)throw new IllegalArgumentException("Informe a pesquisa.");
            BigDecimal custo=ManutencaoPolicy.identificador(filtro.getNumero());
            return estadoCusto(EntityFacadeFactory.getDWFFacade().getJdbcWrapper(),custo);
        } finally {JapeSession.close(h);}
    }

    public Map<String,Object> consultar(ConsultaVinculoRequest filtro) throws Exception {
        JapeSession.SessionHandle h=JapeSession.open();
        try {
            acesso(MGEAuthorizationManager.CONSULTAR);
            if(filtro==null)throw new IllegalArgumentException("Informe a pesquisa.");
            BigDecimal numero=ManutencaoPolicy.identificador(filtro.getNumero());
            BigDecimal empresa=filtro.getEmpresa()==null || filtro.getEmpresa().trim().isEmpty()?null:ManutencaoPolicy.identificador(filtro.getEmpresa());
            int page=filtro.getPagina()==null?0:filtro.getPagina();
            if(page<0 || page>10000)throw new IllegalArgumentException("Página inválida.");
            String type=filtro.getTipo();
            if(!Arrays.asList("NF_NUMERO","NF_NUNOTA","CUSTO_NUNOTA").contains(type))throw new IllegalArgumentException("Tipo de pesquisa inválido.");
            JdbcWrapper jdbc=EntityFacadeFactory.getDWFFacade().getJdbcWrapper();
            boolean documentSearch="NF_NUMERO".equals(type);
            String query=documentSearch?
                "SELECT N.NUNOTA NOTA,N.NUMNOTA NF,N.CODEMP EMPRESA,N.SERIENOTA SERIE,TO_CHAR(N.DTNEG,'DD/MM/YYYY') DATA,P.NOMEPARC CLIENTE FROM TGFCAB N LEFT JOIN TGFPAR P ON P.CODPARC=N.CODPARC WHERE N.TIPMOV='V' AND N.NUMNOTA=:NUMERO ":
                LINK_SELECT+" WHERE "+("NF_NUNOTA".equals(type)?"V.TFV_NUNOTA_VENDA":"V.TFV_NUNOTA_FRETE")+"=:NUMERO ";
            if(empresa!=null)query+=" AND N.CODEMP=:EMPRESA ";
            query+=documentSearch?" ORDER BY N.CODEMP,N.NUNOTA ":" ORDER BY V.TFV_NUNOTA_FRETE,V.TFV_NUNOTA_VENDA ";
            query+=" OFFSET :OFFSET ROWS FETCH NEXT 51 ROWS ONLY";
            NativeSql s=sql(jdbc,query);s.setNamedParameter("NUMERO",numero);s.setNamedParameter("OFFSET",page*50);
            if(empresa!=null)s.setNamedParameter("EMPRESA",empresa);
            List<Map<String,Object>> data=rows(s);boolean more=data.size()>50;if(more)data.remove(50);
            if(!documentSearch)for(Map<String,Object> row:data){
                row.put("versao",version(row));row.put("orfao",row.get("cabecalho")==null);
                row.remove("observacao");row.remove("contabilizado");
            }
            Map<String,Object> response=new LinkedHashMap<>();response.put("tipo",documentSearch?"documentos":"vinculos");
            response.put("itens",data);response.put("mais",more);response.put("pagina",page);
            response.put("podeInativar",autorizado(MGEAuthorizationManager.ALTERAR));
            response.put("podeSanear",autorizado(MGEAuthorizationManager.CONFIGURAR));return response;
        } finally { JapeSession.close(h); }
    }

    public Map<String,Object> inativar(InativarVinculoRequest pedido) throws Exception {
        JapeSession.SessionHandle h=JapeSession.open();
        try {
            AuthenticationInfo auth=acesso(MGEAuthorizationManager.ALTERAR);
            if(pedido==null)throw new IllegalArgumentException("Informe o vínculo.");
            BigDecimal custo=ManutencaoPolicy.identificador(pedido.getCusto()), nota=ManutencaoPolicy.identificador(pedido.getNota());
            String reason=ManutencaoPolicy.motivo(pedido.getMotivo());
            JdbcWrapper jdbc=EntityFacadeFactory.getDWFFacade().getJdbcWrapper();
            NativeSql locked=sql(jdbc,LINK_SELECT+" WHERE V.TFV_NUNOTA_FRETE=:CUSTO AND V.TFV_NUNOTA_VENDA=:NOTA FOR UPDATE OF V.TFV_ATIVO NOWAIT");
            locked.setNamedParameter("CUSTO",custo);locked.setNamedParameter("NOTA",nota);
            List<Map<String,Object>> found=rows(locked);
            if(found.size()!=1)throw new IllegalStateException("Vínculo ausente ou não unívoco.");
            Map<String,Object> row=found.get(0);boolean orphan=row.get("cabecalho")==null;
            // Lock the existing fiscal header while checking financial conditions; never interpret absence as an unposted document.
            if(!orphan){
                NativeSql header=sql(jdbc,"SELECT NUNOTA CABECALHO FROM TGFCAB WHERE NUNOTA=:CUSTO FOR UPDATE NOWAIT");header.setNamedParameter("CUSTO",custo);
                List<Map<String,Object>> headers=rows(header);
                if(headers.size()!=1 || !Objects.equals(headers.get(0).get("cabecalho"),row.get("cabecalho")))throw new IllegalStateException("Documento alterado. Consulte novamente.");
            }
            NativeSql financialLock=sql(jdbc,"SELECT NUFIN FROM TGFFIN WHERE NUNOTA=:CUSTO FOR UPDATE NOWAIT");
            financialLock.setNamedParameter("CUSTO",custo);rows(financialLock);
            Map<String,Object> state=estadoCusto(jdbc,custo);
            if(!Objects.equals(state.get("cabecalho"),row.get("cabecalho")) ||
               !Objects.equals(state.get("contabilizado"),row.get("contabilizado")))
                throw new IllegalStateException("Estado contábil alterado. Consulte novamente.");
            boolean settled=Boolean.TRUE.equals(state.get("baixado"));
            if(settled)throw new IllegalStateException("Há financeiro baixado relacionado ao custo. Operação bloqueada.");
            ManutencaoPolicy.validar((String)row.get("ativo"),version(row),pedido.getVersao(),orphan,
                Boolean.TRUE.equals(pedido.getSaneamentoOrfao()),autorizado(MGEAuthorizationManager.CONFIGURAR),
                (String)row.get("contabilizado"),settled);
            // A second read detects changes observed before DML; it does not promise global accounting isolation.
            Map<String,Object> finalState=estadoCusto(jdbc,custo);
            if(!state.equals(finalState))throw new IllegalStateException("Estado do custo alterado. Consulte novamente.");
            // Audit insert and status update are atomic under Required. Missing audit structure aborts; no fallback to logging only.
            NativeSql audit=sql(jdbc,"INSERT INTO LGC_HIST_VINCULO (ID,CUSTO,NOTA,VALOR,PERCENTUAL,ATIVO_ANTES,ATIVO_DEPOIS,CODUSU,DHALTER,MOTIVO,ORFAO,VERSAO) VALUES (:ID,:CUSTO,:NOTA,:VALOR,:PERCENTUAL,'S','N',:USUARIO,SYSTIMESTAMP,:MOTIVO,:ORFAO,:VERSAO)");
            try {
                audit.setNamedParameter("ID",UUID.randomUUID().toString());audit.setNamedParameter("CUSTO",custo);audit.setNamedParameter("NOTA",nota);
                audit.setNamedParameter("VALOR",row.get("valor"));audit.setNamedParameter("PERCENTUAL",row.get("percentual"));
                audit.setNamedParameter("USUARIO",auth.getUserID());audit.setNamedParameter("MOTIVO",reason);
                audit.setNamedParameter("ORFAO",orphan?"S":"N");audit.setNamedParameter("VERSAO",pedido.getVersao());audit.executeUpdate();
            } finally { NativeSql.releaseResources(audit); }
            NativeSql update=sql(jdbc,"UPDATE TFV_VINCULO_FRETE_VENDA SET TFV_ATIVO='N' WHERE TFV_NUNOTA_FRETE=:CUSTO AND TFV_NUNOTA_VENDA=:NOTA AND TFV_ATIVO='S'");
            try {update.setNamedParameter("CUSTO",custo);update.setNamedParameter("NOTA",nota);update.executeUpdate();if(update.getLastUpdateCount()!=1)throw new IllegalStateException("Vínculo alterado durante a operação.");}
            finally {NativeSql.releaseResources(update);}
            Map<String,Object> response=new LinkedHashMap<>();response.put("sucesso",true);
            response.put("mensagem","Vínculo inativado. Valores preservados, sem redistribuição para outras notas.");return response;
        } catch (Exception e) {
            // Checked SQL/Jape errors must also mark the EJB transaction for rollback.
            throw new IllegalStateException("Inativação não concluída; nenhuma alteração deve ser confirmada. " + e.getMessage(), e);
        } finally { JapeSession.close(h); }
    }
}
