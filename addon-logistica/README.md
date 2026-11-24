# LOGISTICA 1.0.45

Fontes preservados da entrega de 19/10/2026. Este é um projeto Gradle independente do original 1.0.39 mantido na raiz. Consulte o [índice de versões e proveniência](../docs/VERSOES.md).

## Comportamento desta versão

- Consulta de vínculos por NUNOTA da venda, número fiscal com seleção documental ou NUNOTA do custo.
- Inativação de vínculos manuais, sem exclusão física ou redistribuição de valores, com motivo, permissão, controle de concorrência e auditoria transacional.
- Estado contábil obtido em `TCBINT`, origens `E/L`; baixa consultada pelo utilitário nativo `ComercialUtils`.
- Proteção contra reativação automática de vínculo inativo no serviço de rateio complementar.
- Job histórico com fechamento centesimal de CT-e para emissões de janeiro até junho de 2026.
- Classificação `N/I` por nota, compartilhada com o original.

O original permanece proprietário das entidades TFV. `autoDDL=false`; a migração própria cria somente `LGC_HIST_VINCULO` quando ausente. Esta importação preserva o código entregue e não executa alterações no ERP.

## Verificações sem ERP

Na raiz do repositório:

```powershell
node scripts/verify-release.mjs
node addon-logistica/tests/maintenance-ui.test.js
python -m unittest discover -s addon-logistica/tests -v
```

Os testes Java chamados pelo Python requerem JDK 8 em `JAVA_HOME` e usam doubles locais do SDK. Não são homologação de transações/ACL do servidor real. Algumas guardas de preservação comparam arquivos e hashes de versões anteriores.

## Build independente

Java 8 x64 e acesso autorizado ao SDK do fabricante são necessários. A configuração da entrega fixa o plugin 2.20.1. O wrapper compartilhado da raiz aceita `-p`:

```powershell
.\gradlew.bat -p addon-logistica :model:compileJava gerarAddon
```

AppKey e identificação do parceiro são lidas de variáveis de ambiente. Credenciais/chaves de assinatura não pertencem ao Git. Gerar o pacote não instala nem homologa a versão no Sankhya.
