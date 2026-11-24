"""Executes the production controller with an isolated SDK double, never an ERP DB.

The SDK double requires JapeSession before ACL lookup (as the real SDK does).
JDK 8 is required in JAVA_HOME. Production controller and DTOs are compiled intact.
"""
import os, pathlib, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKAGE = pathlib.Path('model/src/main/java/br/com/telemassas/logistica/rateio')
STUBS = {
'br/com/sankhya/jape/core/JapeSession.java': '''package br.com.sankhya.jape.core;
public class JapeSession {
 public static int depth=0, opened=0, closed=0;
 public static class SessionHandle {}
 public static SessionHandle open(){depth++;opened++;return new SessionHandle();}
 public static void close(SessionHandle h){if(h!=null){depth--;closed++;}}
 public static void require(){if(depth==0)throw new IllegalStateException("No JAPE session");}
}''',
'br/com/sankhya/jape/dao/JdbcWrapper.java': 'package br.com.sankhya.jape.dao; public class JdbcWrapper {}',
'br/com/sankhya/modelcore/auth/AuthenticationInfo.java': '''package br.com.sankhya.modelcore.auth;
import java.math.BigDecimal;
public class AuthenticationInfo {public static boolean logged=true;
 public static AuthenticationInfo getCurrent(){return logged?new AuthenticationInfo():null;}
 public BigDecimal getUserID(){return BigDecimal.valueOf(15);}}
''',
'br/com/sankhya/modelcore/auth/MGEAuthorizationManager.java': '''package br.com.sankhya.modelcore.auth;
public class MGEAuthorizationManager {public static final int CONSULTAR=4,ALTERAR=2,CONFIGURAR=5;
 public static final String SIGLA_CONSULTAR="C",SIGLA_ALTERAR="A",SIGLA_CONFIGURAR="G";
 public static int allowed=0;public static boolean resolverFail=false;
 public static MGEAuthorizationManager getMGEResourceAuthorization(String resource,java.math.BigDecimal user){
 br.com.sankhya.jape.core.JapeSession.require();
 if(resolverFail)throw new IllegalStateException("Resolver unavailable");
 if(!resource.equals("addon-logistica.FreteVendaVinculoPOC")||user.intValue()!=15)throw new SecurityException("Wrong principal/resource");
 return new MGEAuthorizationManager();}
 public boolean hasAuthorization(String action){return (allowed==4&&action.equals("C"))||(allowed==2&&action.equals("A"))||(allowed==5&&action.equals("G"));}}''',
'br/com/sankhya/modelcore/auth/MGEAuthorization.java': '''package br.com.sankhya.modelcore.auth;
import br.com.sankhya.jape.core.JapeSession;
public class MGEAuthorization {public static int allowed=0;
 public static void checkAccess(AuthenticationInfo auth,String resource,int permission){
 JapeSession.require();if(permission!=allowed)throw new SecurityException("Denied");}}
''',
'br/com/sankhya/modelcore/comercial/ComercialUtils.java': '''package br.com.sankhya.modelcore.comercial; public class ComercialUtils {public static boolean notaTemFinanceiroBaixado(java.math.BigDecimal n,br.com.sankhya.jape.dao.JdbcWrapper j){return false;}}''',
'br/com/sankhya/modelcore/util/EntityFacadeFactory.java': '''package br.com.sankhya.modelcore.util;
import br.com.sankhya.jape.core.JapeSession;import br.com.sankhya.jape.dao.JdbcWrapper;
public class EntityFacadeFactory {public static EntityFacadeFactory getDWFFacade(){JapeSession.require();return new EntityFacadeFactory();}
 public JdbcWrapper getJdbcWrapper(){return new JdbcWrapper();}}''',
'br/com/sankhya/studio/annotations/enums/EJBTransactionType.java': 'package br.com.sankhya.studio.annotations.enums; public enum EJBTransactionType {Required}',
'br/com/sankhya/studio/annotations/Service.java': '''package br.com.sankhya.studio.annotations;
import br.com.sankhya.studio.annotations.enums.EJBTransactionType;
public @interface Service {String serviceName();EJBTransactionType transactionType();}''',
'br/com/sankhya/jape/sql/NativeSql.java': '''package br.com.sankhya.jape.sql;
import java.sql.*;import java.lang.reflect.*;import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.core.JapeSession;
public class NativeSql {
 public static int reads=0,writes=0;public static boolean fail=false;
 public NativeSql(JdbcWrapper j){JapeSession.require();}public void appendSql(String s){}
 public void setNamedParameter(String s,Object v){}
 public ResultSet executeQuery() throws Exception {JapeSession.require();reads++;
 if(fail)throw new SQLException("simulated read failure");
 ResultSetMetaData meta=(ResultSetMetaData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSetMetaData.class},
 (p,m,a)->{if(m.getName().equals("getColumnCount"))return 0;throw new UnsupportedOperationException(m.getName());});
 return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},
 (p,m,a)->{switch(m.getName()){case "getMetaData":return meta;case "next":return false;case "close":return null;default:throw new UnsupportedOperationException(m.getName());}});}
 public boolean executeUpdate(){writes++;throw new AssertionError("No writes allowed in lifecycle tests");}
 public int getLastUpdateCount(){return 0;}public static void releaseResources(NativeSql s){}
}''',
'SessionLifecycleTest.java': '''import java.util.*;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.modelcore.auth.*;
import br.com.telemassas.logistica.rateio.*;
import br.com.telemassas.logistica.rateio.dto.*;
public class SessionLifecycleTest {
 interface Action {void run() throws Exception;}
 static int count=0;
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static void run(String name,int permission,Class<?> error,int expectedReads,Action action)throws Exception{
 JapeSession.opened=JapeSession.closed=0;NativeSql.reads=NativeSql.writes=0;MGEAuthorizationManager.allowed=permission;
 Throwable found=null;try{action.run();}catch(Throwable e){found=e;}
 check(error==null?found==null:found!=null&&error.isInstance(found),name+" unexpected error: "+found);
 check(JapeSession.depth==0 && JapeSession.opened==1 && JapeSession.closed==1,name+" leaked/unopened session");
 check(NativeSql.reads==expectedReads && NativeSql.writes==0,name+" unauthorized/unexpected DB access");
 count++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception{
 ManutencaoVinculoController c=new ManutencaoVinculoController();
 ConsultaVinculoRequest q=new ConsultaVinculoRequest();q.setTipo("NF_NUNOTA");q.setNumero("28096");
 run("query allowed, write permissions denied",4,null,1,()->{
 Map<String,Object> r=c.consultar(q);check(Boolean.FALSE.equals(r.get("podeInativar")),"must preserve ALTERAR");
 check(Boolean.FALSE.equals(r.get("podeSanear")),"must preserve CONFIGURAR");});
 run("query denied before data",0,SecurityException.class,0,()->c.consultar(q));
 run("query null request",4,IllegalArgumentException.class,0,()->c.consultar(null));
 q.setNumero("x");run("query invalid ID",4,IllegalArgumentException.class,0,()->c.consultar(q));q.setNumero("28096");
 NativeSql.fail=true;run("query DB error releases session",4,java.sql.SQLException.class,1,()->c.consultar(q));NativeSql.fail=false;
 run("inactivation denied before data",0,IllegalStateException.class,0,()->c.inativar(null));
 run("inactivation null request",2,IllegalStateException.class,0,()->c.inativar(null));
 MGEAuthorizationManager.resolverFail=true;run("ACL resolver failure denies before data",4,IllegalStateException.class,0,()->c.consultar(q));MGEAuthorizationManager.resolverFail=false;
 run("cost state query denied before data",0,SecurityException.class,0,()->c.consultarEstadoCusto(q));
 run("cost state null request",4,IllegalArgumentException.class,0,()->c.consultarEstadoCusto(null));
 run("cost state empty result fails closed",4,IllegalStateException.class,1,()->c.consultarEstadoCusto(q));
 AuthenticationInfo.logged=false;run("unauthenticated query",4,SecurityException.class,0,()->c.consultar(q));
 System.out.println(count+" lifecycle tests passed; zero writes");}}
'''
}

class SessionLifecycle(unittest.TestCase):
    def test_actual_controller_lifecycle(self):
        java = pathlib.Path(os.environ['JAVA_HOME'])/'bin'
        controller_root = pathlib.Path(os.environ.get('LGC_CONTROLLER_ROOT', str(ROOT)))
        with tempfile.TemporaryDirectory(prefix='lgc-session-') as tmp:
            tmp = pathlib.Path(tmp)
            files=[]
            for name, source in STUBS.items():
                f=tmp/name; f.parent.mkdir(parents=True,exist_ok=True)
                f.write_text(source,encoding='utf8'); files.append(str(f))
            files.extend(str(controller_root/PACKAGE/name) for name in ['ManutencaoVinculoController.java','ManutencaoPolicy.java','ContabilizacaoCustoSql.java','dto/ConsultaVinculoRequest.java','dto/InativarVinculoRequest.java'])
            subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(tmp)]+files,check=True,capture_output=True)
            result=subprocess.run([str(java/'java.exe'),'-cp',str(tmp),'SessionLifecycleTest'],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            print(result.stdout)

if __name__=='__main__':unittest.main(verbosity=2)
