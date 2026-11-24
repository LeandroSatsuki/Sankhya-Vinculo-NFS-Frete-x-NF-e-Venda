'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../vc/src/main/webapp/html5/FreteVendaVinculoPOC/FreteVendaVinculoPOC.js'),'utf8');
function harness(handler,href){
 let ctor;const calls=[];
 const angular={module:()=>({controller:(name,arr)=>{ctor=arr[arr.length-1];}}),copy:x=>JSON.parse(JSON.stringify(x)),isArray:Array.isArray,forEach:(obj,f)=>{if(Array.isArray(obj))obj.forEach(f);else Object.keys(obj||{}).forEach(k=>f(obj[k],k));}};
 vm.runInNewContext(source,{angular,console});
 const http={post:(url,body)=>{calls.push({url,body});return Promise.resolve().then(()=>handler(url,body));}};
 const win={location:{href:href||'file:///local.html'},history:{back(){}},setTimeout(){}};
 return {ctrl:new ctor(win,http,{all:Promise.all.bind(Promise),when:Promise.resolve.bind(Promise)}),calls};
}
function ok(body){return {data:{status:'1',responseBody:body}};}
async function run(){
 let h=harness(()=>ok({tipo:'vinculos',itens:[{custo:30020,nota:28096,ativo:'S',orfao:true,versao:'a'}],pagina:0,mais:false,podeInativar:true,podeSanear:true}));
 h.ctrl.lookup.numero='28096';await h.ctrl.searchLinks(0);
 assert.equal(h.calls[0].body.requestBody.filtro.tipo,'NF_NUNOTA');assert.equal(h.ctrl.maintenance.items[0].custo,30020);
 h.ctrl.prepareInactivation(h.ctrl.maintenance.items[0]);assert.equal(h.ctrl.maintenance.orphanConsent,false);
 h.ctrl.lookup.numero='ALTERADO';await h.ctrl.maintenancePage(0);assert.equal(h.calls[1].body.requestBody.filtro.numero,'28096','paginação preserva recorte consultado');
 h=harness(()=>ok({tipo:'documentos',itens:[{nota:1,nf:60,empresa:1},{nota:2,nf:60,empresa:4}],mais:false}));
 h.ctrl.lookup={tipo:'NF_NUMERO',numero:'60',empresa:''};await h.ctrl.searchLinks();assert.equal(h.ctrl.maintenance.items.length,2);assert.equal(h.ctrl.maintenance.documents,true);
 await h.ctrl.selectDocument({nota:2});assert.equal(h.calls[1].body.requestBody.filtro.numero,'2');assert.equal(h.calls[1].body.requestBody.filtro.tipo,'NF_NUNOTA');
 h=harness(()=>Promise.reject({data:{statusMessage:'Sem permissão'}}));h.ctrl.lookup.numero='1';await h.ctrl.searchLinks();assert.equal(h.ctrl.maintenance.error,'Sem permissão');assert.equal(h.ctrl.maintenance.items.length,0);
 let stored='S';h=harness((url,body)=>{
  if(body.serviceName.endsWith('.inativar')){assert.equal(body.requestBody.pedido.custo,'30020');assert.equal(body.requestBody.pedido.nota,'28096');assert.equal(body.requestBody.pedido.versao,'v1');assert.equal(body.requestBody.pedido.saneamentoOrfao,true);stored='N';return ok({sucesso:true,mensagem:'Inativado'});}
  return ok({tipo:'vinculos',itens:[{custo:30020,nota:28096,ativo:stored,orfao:true,versao:'v1'}],podeInativar:true,podeSanear:true});
 });h.ctrl.lookup.numero='28096';await h.ctrl.searchLinks();h.ctrl.prepareInactivation(h.ctrl.maintenance.items[0]);h.ctrl.maintenance.reason='Vínculo órfão confirmado';h.ctrl.maintenance.orphanConsent=true;await h.ctrl.confirmInactivation();assert.equal(h.ctrl.maintenance.items[0].ativo,'N');assert.equal(h.ctrl.maintenance.pending,null);assert.equal(h.ctrl.maintenance.message,'Inativado');
 h.ctrl.prepareInactivation(h.ctrl.maintenance.items[0]);assert.equal(h.ctrl.maintenance.pending,null,'inativo não pode abrir ação');
 assert(!source.includes('CRUDServiceProvider.removeRecord'),'nenhuma exclusão física no novo frontend');
 assert(!source.includes('TFV_CTE_NFE_RATEIO'),'manutenção não toca rateio automático');
 h=harness((url,body)=>{
  if(body.serviceName==='LGCManutencaoVinculoSP.consultarEstadoCusto') return ok({contabilizado:'S',baixado:true});
  const entity=body.requestBody.dataSet.rootEntity;
  assert.notEqual(entity,'Financeiro','status financeiro deve vir do serviço central');
  assert(!body.requestBody.dataSet.entity.fieldset.list.includes('CONTABILIZADO'),'não solicitar campo físico inexistente');
  return ok({entities:{entity:[]}});
 },'file:///local.html?NUNOTA=31824');
 await h.ctrl.init(); await new Promise(resolve=>setImmediate(resolve));
 assert.equal(h.ctrl.accounted,'Sim'); assert.equal(h.ctrl.settledInstallments,'Sim');
 h=harness(()=>Promise.reject({data:{statusMessage:'Estado indisponível'}}),'file:///local.html?NUNOTA=31824');
 await h.ctrl.init(); await new Promise(resolve=>setImmediate(resolve));
 assert.equal(h.ctrl.accounted,'Não consultado');assert.equal(h.ctrl.settledInstallments,'Não consultado');
 console.log('PASS: busca inversa, documento repetido, paginação congelada, erro, inativação nominal, refresh e ausência de exclusão física.');
}
run().catch(e=>{console.error(e);process.exitCode=1;});
