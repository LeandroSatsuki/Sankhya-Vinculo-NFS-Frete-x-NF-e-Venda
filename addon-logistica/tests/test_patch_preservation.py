"""Narrow production delta against the preserved installed 1.0.44 source."""
import hashlib,json,pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class PatchPreservation(unittest.TestCase):
 def test_sources_and_sql_unchanged(self):
  baseline=json.loads((ROOT/'tests/fixtures/source-1044-preservado.json').read_text(encoding='utf8'))['files']
  allowed={'model/src/main/java/br/com/telemassas/logistica/rateio/ManutencaoVinculoController.java','model/src/main/java/br/com/telemassas/logistica/rateio/ManutencaoPolicy.java','vc/src/main/webapp/html5/FreteVendaVinculoPOC/FreteVendaVinculoPOC.js'}
  changed={n for n,h in baseline.items() if hashlib.sha256((ROOT/n).read_bytes()).hexdigest()!=h}
  self.assertEqual(changed,allowed)
  current={f.relative_to(ROOT).as_posix() for folder in ['model/src','vc/src','datadictionary','dbscripts'] for f in (ROOT/folder).rglob('*') if f.is_file()}
  self.assertEqual(current-set(baseline),{'model/src/main/java/br/com/telemassas/logistica/rateio/ContabilizacaoCustoSql.java'})
