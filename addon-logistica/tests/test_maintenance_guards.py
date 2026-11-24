"""Runs the actual controller with deterministic DB/SDK doubles, never ERP writes."""
import pathlib, os, subprocess, tempfile, unittest
from test_session_lifecycle import STUBS, ROOT, PACKAGE

SQL = '''package br.com.sankhya.jape.sql;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import br.com.sankhya.jape.dao.JdbcWrapper;
public class NativeSql {
 public static List<List<Map<String,Object>>> plan=new ArrayList<>();public static int reads,writes,failAt;
 String text=""; public NativeSql(JdbcWrapper j){} public void appendSql(String s){text+=s;}
 public void setNamedParameter(String k,Object v){}
 public ResultSet executeQuery()throws Exception{
  reads++;if(reads==failAt)throw new SQLException("Lock/read failed");
  final List<Map<String,Object>> data=plan.remove(0);final String[] keys=data.isEmpty()?new String[0]:data.get(0).keySet().toArray(new String[0]);final int[] pos={-1};
  ResultSetMetaData meta=(ResultSetMetaData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSetMetaData.class},(p,m,a)->{
   if(m.getName().equals("getColumnCount"))return keys.length;if(m.getName().equals("getColumnLabel"))return keys[(Integer)a[0]-1];throw new UnsupportedOperationException();});
  return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->{
   switch(m.getName()){case "next":return ++pos[0]<data.size();case "getMetaData":return meta;case "getObject":return data.get(pos[0]).get(keys[(Integer)a[0]-1]);case "close":return null;default:throw new UnsupportedOperationException();}});
 }
 public boolean executeUpdate(){if(!text.startsWith("INSERT INTO LGC_HIST_VINCULO")&&!text.startsWith("UPDATE TFV_VINCULO_FRETE_VENDA SET TFV_ATIVO='N'"))throw new AssertionError("Unexpected write");writes++;return true;}
 public int getLastUpdateCount(){return 1;}public static void releaseResources(NativeSql s){}
}'''
TEST = '''import java.math.*;import java.util.*;import java.lang.reflect.*;
import br.com.sankhya.jape.sql.NativeSql;import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.modelcore.comercial.ComercialUtils;import br.com.sankhya.modelcore.auth.*;
import br.com.telemassas.logistica.rateio.*;import br.com.telemassas.logistica.rateio.dto.*;
public class MaintenanceGuardsTest {
 static int count; static BigDecimal id=new BigDecimal("30020");
 static Map<String,Object> map(Object...v){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
 static void add(Map<String,Object> row){NativeSql.plan.add(Collections.singletonList(row));}
 static void run(String name,boolean orphan,String status,boolean settled,boolean configure,boolean consent,boolean stale,boolean change,boolean helperError,int failAt,boolean pass)throws Exception{
  NativeSql.plan.clear();NativeSql.reads=NativeSql.writes=0;NativeSql.failAt=failAt;
  MGEAuthorizationManager.allowed=configure?7:2;ComercialUtils.settled=settled;ComercialUtils.fail=helperError;
  Map<String,Object> link=map("custo",id,"nota",new BigDecimal("28096"),"valor",new BigDecimal("4709.26"),"percentual",new BigDecimal("94.1852"),"ativo","S","origem","M","observacao","teste","cabecalho",orphan?null:id,"nf_custo",null,"total_custo",null,"contabilizado",status);
  ManutencaoVinculoController c=new ManutencaoVinculoController();Method v=c.getClass().getDeclaredMethod("version",Map.class);v.setAccessible(true);
  InativarVinculoRequest p=new InativarVinculoRequest();p.setCusto("30020");p.setNota("28096");p.setMotivo("Teste isolado sem banco real");p.setVersao(stale?"antiga":(String)v.invoke(c,link));p.setSaneamentoOrfao(consent);
  add(link);if(!orphan)add(map("cabecalho",id));NativeSql.plan.add(Collections.emptyList());
  add(map("cabecalho",orphan?null:id,"contabilizado",status));add(map("cabecalho",orphan?null:id,"contabilizado",change?"S":status));
  Throwable failure=null;try{c.inativar(p);}catch(Throwable e){failure=e;}
  if(pass?(failure!=null||NativeSql.writes!=2):(failure==null||NativeSql.writes!=0))throw new AssertionError(name+" failure="+failure+" writes="+NativeSql.writes);
  if(JapeSession.depth!=0)throw new AssertionError("Session leaked");count++;System.out.println("PASS "+name);
 }
 public static void main(String[] a)throws Exception{
  run("existing clean cost",false,"N",false,false,false,false,false,false,0,true);
  run("orphan explicit configured cleanup",true,null,false,true,true,false,false,false,0,true);
  run("orphan without CONFIGURAR",true,null,false,false,true,false,false,false,0,false);
  run("orphan without consent",true,null,false,true,false,false,false,false,0,false);
  run("posted existing cost",false,"S",false,true,true,false,false,false,0,false);
  run("posted orphan",true,"S",false,true,true,false,false,false,0,false);
  run("settled existing cost",false,"N",true,true,true,false,false,false,0,false);
  run("settled orphan",true,null,true,true,true,false,false,false,0,false);
  run("unknown existing cost",false,null,false,true,true,false,false,false,0,false);
  run("stale token",false,"N",false,true,true,true,false,false,0,false);
  run("accounting state changes",false,"N",false,true,true,false,true,false,0,false);
  run("native financial check fails",false,"N",false,true,true,false,false,true,0,false);
  run("header lock fails",false,"N",false,true,true,false,false,false,2,false);
  System.out.println(count+" real-controller guard scenarios; simulated writes only");
 }
}'''

class MaintenanceGuards(unittest.TestCase):
 def test_actual_controller_guards(self):
  stubs=dict(STUBS)
  del stubs['SessionLifecycleTest.java']
  stubs['br/com/sankhya/jape/sql/NativeSql.java']=SQL
  k='br/com/sankhya/modelcore/auth/MGEAuthorizationManager.java'
  stubs[k]=stubs[k].replace('return (allowed==4', 'return allowed==7 || (allowed==4')
  stubs['br/com/sankhya/modelcore/comercial/ComercialUtils.java']='''package br.com.sankhya.modelcore.comercial;
public class ComercialUtils {public static boolean settled,fail;
 public static boolean notaTemFinanceiroBaixado(java.math.BigDecimal n,br.com.sankhya.jape.dao.JdbcWrapper j)throws Exception{if(fail)throw new Exception("Unavailable");return settled;}}'''
  stubs['MaintenanceGuardsTest.java']=TEST
  java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
  with tempfile.TemporaryDirectory(prefix='lgc-guards-') as d:
   tmp=pathlib.Path(d);files=[]
   for name,source in stubs.items():
    f=tmp/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(source,encoding='utf8');files.append(str(f))
   files += [str(ROOT/PACKAGE/n) for n in ['ManutencaoVinculoController.java','ManutencaoPolicy.java','ContabilizacaoCustoSql.java','dto/ConsultaVinculoRequest.java','dto/InativarVinculoRequest.java']]
   c=subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',d]+files,capture_output=True,text=True)
   self.assertEqual(c.returncode,0,c.stdout+c.stderr)
   c=subprocess.run([str(java/'java.exe'),'-cp',d,'MaintenanceGuardsTest'],capture_output=True,text=True)
   self.assertEqual(c.returncode,0,c.stdout+c.stderr);print(c.stdout)
