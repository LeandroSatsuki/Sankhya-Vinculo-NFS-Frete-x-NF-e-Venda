"""Exports the real production LINK_SELECT for Oracle proof; never connects to DB."""
import os,pathlib,tempfile,subprocess
from test_session_lifecycle import STUBS,ROOT,PACKAGE
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory(prefix='lgc-export-') as d:
 tmp=pathlib.Path(d);files=[]
 for n,s in STUBS.items():
  if n=='SessionLifecycleTest.java':continue
  f=tmp/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(s,encoding='utf8');files.append(str(f))
 f=tmp/'Export.java';f.write_text('''import br.com.telemassas.logistica.rateio.ManutencaoVinculoController;public class Export {public static void main(String[] a)throws Exception {java.lang.reflect.Field f=ManutencaoVinculoController.class.getDeclaredField("LINK_SELECT");f.setAccessible(true);System.out.print((String)f.get(null)+" WHERE V.TFV_NUNOTA_VENDA=28096 ORDER BY V.TFV_NUNOTA_FRETE,V.TFV_NUNOTA_VENDA OFFSET 0 ROWS FETCH NEXT 51 ROWS ONLY");}}''',encoding='utf8');files.append(str(f))
 files += [str(ROOT/PACKAGE/n) for n in ['ManutencaoVinculoController.java','ManutencaoPolicy.java','ContabilizacaoCustoSql.java','dto/ConsultaVinculoRequest.java','dto/InativarVinculoRequest.java']]
 c=subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',d]+files,capture_output=True,text=True);c.check_returncode()
 c=subprocess.run([str(java/'java.exe'),'-cp',d,'Export'],capture_output=True,text=True);c.check_returncode();print(c.stdout)
