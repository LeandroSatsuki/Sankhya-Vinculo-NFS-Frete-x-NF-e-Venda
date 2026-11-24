# Projetos e versões

Este repositório contém **dois add-ons independentes que coexistem**. A pasta do projeto é parte da identificação da versão.

| Projeto | Fontes | Versão registrada | Contexto / serviços |
|---|---|---|---|
| Original / Vinculo Notas | Raiz | 1.0.39 | `/addon-template`, prefixo `TFV` |
| LOGISTICA / VINCULO FRETE | [`addon-logistica/`](../addon-logistica/) | 1.0.45 | `/addon-logistica`, prefixo `LGC` |

A raiz mantém o original que cria e fornece as entidades TFV. A LOGISTICA usa essas entidades, mantém sua auditoria em `LGC_HIST_VINCULO` e não deve recriar o dicionário do original. Cada projeto tem seu próprio `settings.gradle`.

## Proveniência da 1.0.45

Os fontes produtivos e os testes foram importados da entrega local `fonte-logistica-1.0.45.zip`, de 19/10/2026. O pacote e o arquivo de fontes foram conferidos contra os hashes registrados na entrega. Os bytes dos arquivos incluídos no manifesto foram preservados; documentação editorial e saídas geradas não são reconstrução do binário.

- [Manifesto de hashes](../releases/logistica-1.0.45.json).
- [Índice vigente](../releases/index.json).
- SHA-256 do ZIP de fontes: `f655e32d85d66200a92b5d35dc023774f1f7029b7936b553433940afcca99041`.
- SHA-256 do pacote `.exts`: `4a93d04b749ea7403c199f369cba75a9027338a3c6402377a0199a5af238cc6a`.

Os artefatos binários e evidências operacionais ficam fora do Git. Um build novo pode ter metadados/assinatura diferentes; comparar somente a versão não prova igualdade binária com a instalação.

## Vínculo com a margem

Os dois add-ons alimentam `TFV_VINCULO_FRETE_VENDA`, `TFV_CTE` e `TFV_CTE_NFE_RATEIO`, consumidas pelo relatório de margem. A manutenção 1.0.45 inativa vínculos manuais com auditoria e preserva os valores; um consumidor deve respeitar `TFV_ATIVO='S'`.

O job complementar `LGCAtualizaCteHistoricoSP` trabalha com emissões de janeiro até junho de 2026; o job original mantém o período a partir de julho de 2026. Ambos usam frequência declarada de cinco minutos. Consultar o código antes de ampliar períodos, para evitar sobreposição dos produtores.

A classificação logística continua por `NUNOTA` em `TFV_CLASSIFICACAO_LOGISTICA`: `N` normal e `I` direto da indústria. O [contrato de classificação](CLASSIFICACAO_LOGISTICA.md) prevê isentar movimentação/armazenagem para `I`, preservando frete e CT-e. A importação destes fontes não altera nem implementa essa integração no dashboard.

## Próximas entregas

1. Alterar o projeto correto e atribuir uma nova versão; preservar o outro projeto.
2. Executar os testes e compilar com o SDK/JDK apropriados em ambiente autorizado.
3. Criar um novo manifesto com hashes dos fontes produtivos, ZIP e pacote. Preservar os manifestos históricos.
4. Atualizar `releases/index.json`, README e changelog. Executar `node scripts/verify-release.mjs`.
5. Versionar os fontes antes de publicar um pacote no portal. Registrar o commit da entrega e usar uma tag específica, por exemplo `logistica-v1.0.46`.
6. Registrar instalação, versão e hash efetivamente verificados separadamente da geração do pacote. Instalar ou publicar no Sankhya exige o escopo autorizado para essa ação.

O verificador valida o manifesto vigente. Manifestos de versões antigas representam seus commits/tags, não devem ser recalculados para acomodar alterações futuras.
