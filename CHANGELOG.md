# Changelog

Todas as mudanças relevantes deste projeto serão documentadas aqui. O projeto adotará Versionamento Semântico quando existir um artefato versionável.

## [1.0.39] - 2026-09-15

### Added

- Classificação logística por NUNOTA (`N` movimentação normal ou `I` direto da indústria), com auditoria de usuário/data, aviso visual e persistência transacional junto ao rateio de frete.

- Estrutura inicial de diagnóstico, premissas, decisões arquiteturais, roadmap, testes, rollback e investigação.
- Política inicial de segurança e ignore de arquivos locais/sensíveis.
- Modelo `.env.example` sem credenciais.
- Registro da versão Sankhya Om `4.36b110`, TOP `1485` e teste OAuth de homologação.
- Evidências somente leitura das amostras de frete `29920` e venda `29811`.
- Registro da divergência entre descritores públicos e os aceitos pela entidade no ambiente.
- Confirmação funcional do valor-base de R$ 200,00, da atribuição integral à venda 29811 e da Central de Compras como tela do processo.
- Registro do fluxo Portal de Compras → Central de Compras → Liberação de Limites e da política funcional de alteração/exclusão pós-confirmação.
- Decisão funcional: vínculos podem ser incluídos/alterados após confirmação até baixa/contabilização; exclusão da nota remove vínculos logicamente com auditoria preservada.
- Registro dos indicadores funcionais de baixa, contabilização, exclusão por ausência do cabeçalho e autorização por usuário; proposta de status técnico `INATIVO` sem histórico operacional visível.
- Consulta somente leitura confirmou `Financeiro.DHBAIXA` e `Financeiro.CTABCOBAIXA` como indicadores presentes na parcela da nota `29920`, atualmente sem baixa.
- Validação inicial somente leitura de `CabecalhoNota.CONTABILIZADO` na nota `29920`; a consulta usava calculados desabilitados.
- Correção da consulta com campos calculados habilitados: `CONTABILIZADO=N` na nota `29920`; domínio `S/N` confirmado pela tela do Dicionário.
- Sanitização do template extraído: `appKey` e nome do parceiro passaram a ser lidos por ambiente; `template.zip` ficou ignorado.
- Diagnóstico do bloqueio do Gradle: plugin requer biblioteca nativa Windows x64 e a estação atual usa Java 8 32-bit.
- Regra funcional registrada: qualquer parcela baixada bloqueia alterações da nota e dos vínculos, inclusive em baixa parcial.
- Regra validada em homologação: uma única parcela baixada bloqueou a alteração da nota.
- Direção de POC escolhida: botão de ação contextual abrindo pop-up de vínculos.

### Security

- Gate explícito que impede implementação antes da confirmação de campos, versões, permissões, SGBD e regras funcionais.
- Autenticação testada sem registrar token, client secret ou X-Token e sem habilitar escrita.
