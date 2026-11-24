"""Offline properties plus exact SQL/source guards. No ERP connections."""
import unittest, random, re, json, hashlib
from pathlib import Path
from decimal import Decimal as D, ROUND_HALF_UP, getcontext
getcontext().prec=50
ROOT=Path(__file__).resolve().parents[1]
JOB=Path('model/src/main/java/br/com/telemassas/logistica/jobs/AtualizaCteNfeJob.java')
FIXTURES=ROOT/'tests/fixtures'
def round2(v): return v.quantize(D('.01'),rounding=ROUND_HALF_UP)
def allocate(total, weights):
    keys=sorted(weights); den=sum(weights.values(),D(0))
    raw={k:total*weights[k]/den if den>0 else total/len(keys) for k in keys}
    rounded={k:round2(raw[k]) for k in keys}
    cents=int(((total-sum(rounded.values()))*100).to_integral_value(rounding=ROUND_HALF_UP))
    sign=D(1 if cents>0 else -1 if cents<0 else 0)
    order=sorted(keys,key=lambda k:(-sign*(raw[k]-rounded[k]),k))
    for k in order[:abs(cents)]: rounded[k]+=sign/D(100)
    return rounded,raw,cents
class Centavos(unittest.TestCase):
    def test_closed_unchanged(self):
        out,raw,c=allocate(D('100'),{'a':D(1),'b':D(3)})
        self.assertEqual(c,0);self.assertEqual(out,{'a':D('25'),'b':D('75')})
    def test_positive_negative_residual_and_zero_weights(self):
        for total in [D('.01'),D('.02'),D('4250'),D('-0.01'),D('-4250'),D(0)]:
            for weights in [{'a':D(1),'b':D(1),'c':D(1)},dict.fromkeys([str(i) for i in range(16)],D(0))]:
                out,raw,c=allocate(total,weights)
                self.assertEqual(sum(out.values()),total)
                self.assertTrue(all(abs(out[k]-round2(raw[k]))<=D('.01') for k in out))
                self.assertTrue(all(v>=0 for v in out.values()) if total>=0 else all(v<=0 for v in out.values()))
    def test_order_independent_ties(self):
        a={'c':D(1),'b':D(1),'a':D(1)}
        self.assertEqual(allocate(D('.01'),a)[0],allocate(D('.01'),dict(reversed(list(a.items()))))[0])
    def test_random_conservation_and_preservation(self):
        rnd=random.Random(1042)
        for _ in range(3000):
            total=D(rnd.randrange(-10000000,10000000))/100
            weights={f'{i:04}':D(rnd.randrange(0,100000))/100 for i in range(rnd.randrange(1,81))}
            out,raw,c=allocate(total,weights)
            self.assertEqual(sum(out.values()),total)
            self.assertTrue(all(abs(out[k]-round2(raw[k]))<=D('.01') for k in out))
            if c==0:self.assertEqual(out,{k:round2(v) for k,v in raw.items()})
    def test_source_preserves_population_and_transaction(self):
        old=(FIXTURES/'job-1041.java.txt').read_text(encoding='utf8')
        new=(ROOT/JOB).read_text(encoding='utf8')
        prefix='            NativeSql allocation =';suffix='            allocation.executeUpdate();'
        self.assertEqual(old.split(prefix)[0],new.split(prefix)[0])
        self.assertEqual(old.split(suffix)[1],new.split(suffix)[1])
        self.assertIn('transactionType = EJBTransactionType.Required',new)
        fragments=lambda s:re.findall(r'allocation.appendSql\("(.*)"\);',s)
        sql=''.join(fragments(new)); oldsql=''.join(fragments(old))[len('INSERT INTO TFV_CTE_NFE_RATEIO (TFV_CHAVE_CTE,TFV_CHAVE_NFE,TFV_PESO_NFE,TFV_PESO_TOTAL,TFV_VALOR_RATEADO) '):]
        base=oldsql.replace('SELECT R.CHAVE_CTE, R.CHAVE_NFE, R.PESO_NFE, R.PESO_TOTAL, CASE','SELECT R.CHAVE_CTE, R.CHAVE_NFE, R.PESO_NFE, R.PESO_TOTAL, C.TFV_VALOR_TOTAL AS ORIGINAL, CASE',1).replace('END FROM (SELECT B.','END AS BRUTO FROM (SELECT B.',1)
        self.assertIn(base,sql)
        self.assertIn('ORDER BY SIGN(D.CENTAVOS) * (D.BRUTO - D.ARREDONDADO) DESC, D.CHAVE_NFE',sql)
        self.assertIn('SIGN(Z.CENTAVOS) / 100',sql)
    def test_other_source_files_unchanged(self):
        hashes=json.loads((FIXTURES/'source-1041-hashes.json').read_text(encoding='utf8'))
        changed=[]
        for name,expected_hash in hashes.items():
            f=ROOT/name
            if f.is_file() and hashlib.sha256(f.read_bytes()).hexdigest()!=expected_hash:changed.append(name)
        self.assertEqual(sorted(changed),sorted(['build.gradle',JOB.as_posix(),'model/src/main/java/br/com/telemassas/logistica/rateio/RateioFreteController.java','vc/src/main/webapp/html5/FreteVendaVinculoPOC/FreteVendaVinculoPOC.html','vc/src/main/webapp/html5/FreteVendaVinculoPOC/FreteVendaVinculoPOC.js','vc/src/main/webapp/html5/FreteVendaVinculoPOC/FreteVendaVinculoPOC.css','dbscripts/README.md','README.md','CHANGELOG.md']))
if __name__=='__main__':unittest.main(verbosity=2)
