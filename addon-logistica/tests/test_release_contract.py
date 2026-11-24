import unittest, pathlib, re, xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
CONTROLLER=ROOT/'model/src/main/java/br/com/telemassas/logistica/rateio/ManutencaoVinculoController.java'

class ReleaseContract(unittest.TestCase):
    def test_no_foreign_metadata_or_schema(self):
        tables=[]
        for f in (ROOT/'datadictionary').glob('*.xml'):
            tables += [e.attrib['name'] for e in ET.parse(f).getroot().iter('table')]
        self.assertEqual(tables,['LGC_HIST_VINCULO'])
        migration=ET.parse(ROOT/'dbscripts/V1_historico_vinculo.xml').getroot()
        entries=list(migration.iter('sql'));self.assertEqual(len(entries),1)
        self.assertEqual(entries[0].get('executar'),'SE_NAO_EXISTIR')
        ddl=entries[0].find('oracle').text
        self.assertIn('CREATE TABLE LGC_HIST_VINCULO',ddl)
        self.assertNotRegex(ddl,r'(?i)\b(DROP|ALTER|UPDATE|DELETE|TGFCAB|TFV_)\b')
        self.assertIn('VALOR NUMBER NOT NULL',ddl)
        self.assertIn('DHALTER TIMESTAMP',ddl)
    def test_status_only_and_atomic_audit(self):
        s=CONTROLLER.read_text(encoding='utf8')
        writes=re.findall(r'UPDATE TFV_VINCULO_FRETE_VENDA SET ([^"\n]+)',s)
        self.assertEqual(len(writes),1)
        self.assertTrue(writes[0].startswith("TFV_ATIVO='N' WHERE"))
        self.assertIn('FOR UPDATE OF V.TFV_ATIVO NOWAIT',s)
        self.assertLess(s.index('audit.executeUpdate()'),s.index('update.executeUpdate()'))
        self.assertIn('update.getLastUpdateCount()!=1',s)
        self.assertIn('catch (Exception e)',s)
        self.assertIn('throw new IllegalStateException("Inativação não concluída',s)
        self.assertIn('transactionType=EJBTransactionType.Required',s)
        self.assertNotIn('DELETE FROM',s)
        self.assertNotIn('TFV_CTE_NFE_RATEIO',s)
    def test_permissions_and_unknown_financial_status(self):
        s=CONTROLLER.read_text(encoding='utf8')
        self.assertNotIn('MGEAuthorization.checkAccess',s)
        self.assertIn('getMGEResourceAuthorization(RESOURCE,auth.getUserID()).hasAuthorization(action)',s)
        for action in ['CONSULTAR','ALTERAR','CONFIGURAR']:
            self.assertIn('MGEAuthorizationManager.SIGLA_'+action,s)
        self.assertIn('acesso(MGEAuthorizationManager.CONSULTAR)',s)
        self.assertIn('acesso(MGEAuthorizationManager.ALTERAR)',s)
        self.assertIn('autorizado(MGEAuthorizationManager.CONFIGURAR)',s)
        self.assertNotIn('NVL(F.CONTABILIZADO',s)
        self.assertIn('ComercialUtils.notaTemFinanceiroBaixado(custo,jdbc)',s)
        self.assertNotIn('F.CONTABILIZADO',s)
        self.assertNotIn('DTBAIXA',s)
    def test_complement_no_silent_reactivation(self):
        s=(ROOT/'model/src/main/java/br/com/telemassas/logistica/rateio/RateioFreteController.java').read_text(encoding='utf8')
        self.assertIn("WHERE DEST.TFV_ATIVO = 'S'",s)
        self.assertIn('bloquearReativacao(rateio);\n            salvarVinculos(rateio);\n            bloquearReativacao(rateio);',s)
    def test_fiscal_number_requires_selection(self):
        s=CONTROLLER.read_text(encoding='utf8')
        self.assertIn('N.NUMNOTA=:NUMERO',s)
        self.assertIn('N.SERIENOTA SERIE',s)
        self.assertIn('V.TFV_NUNOTA_VENDA',s)
        self.assertIn('LEFT JOIN TGFCAB F',s)
        self.assertIn('FETCH NEXT 51 ROWS ONLY',s)
        self.assertNotIn('R$ 4.709',s)
    def test_integrated_cent_candidate_is_identical(self):
        # Compares the concrete Java producer with the source used in the recorded Oracle proof.
        import hashlib
        fixture=ROOT/'tests/fixtures/job-centavos-aprovado.java.txt'
        job=ROOT/'model/src/main/java/br/com/telemassas/logistica/jobs/AtualizaCteNfeJob.java'
        self.assertEqual(hashlib.sha256(job.read_bytes()).hexdigest(),hashlib.sha256(fixture.read_bytes()).hexdigest())

if __name__=='__main__':unittest.main(verbosity=2)
