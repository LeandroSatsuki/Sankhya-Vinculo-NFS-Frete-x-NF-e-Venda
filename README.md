# Vínculo de Frete x Venda — Sankhya Om

<p align="center">
  Add-on para vincular documentos de frete às respectivas notas de venda e distribuir custos proporcionalmente ao peso bruto.
</p>

<p align="center">
  <img alt="Sankhya Om" src="https://img.shields.io/badge/Sankhya_Om-005CA9?style=flat-square">
  <img alt="Java" src="https://img.shields.io/badge/Java_8-ED8B00?style=flat-square&logo=openjdk&logoColor=white">
  <img alt="JavaScript" src="https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&logo=javascript&logoColor=black">
  <img alt="Oracle" src="https://img.shields.io/badge/Oracle_SQL-F80000?style=flat-square&logo=oracle&logoColor=white">
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white">
</p>

## Sobre o projeto

Este add-on foi desenvolvido para a operação da Telemassas com o objetivo de automatizar o vínculo entre notas de serviço de frete, notas fiscais de venda e CT-e dentro do Sankhya Om.

A solução reduz cálculos manuais, mantém a rastreabilidade dos vínculos e garante que o valor do frete seja distribuído entre as vendas selecionadas de acordo com o peso bruto de cada nota.

## Problemas resolvidos

- identificação das vendas relacionadas a um documento de frete;
- rateio proporcional por peso bruto;
- fechamento exato do valor total, inclusive diferenças de arredondamento;
- bloqueio de alterações quando existem parcelas baixadas ou contabilização;
- armazenamento estruturado dos vínculos para consulta e auditoria;
- reprocessamento de rateios por exclusão controlada dos vínculos;
- sincronização de CT-e e respectivas NF-e referenciadas no XML.

## Fluxo funcional

```text
Nota de frete
      │
      ▼
validação financeira e contábil
      │
      ▼
seleção das NF-e de venda
      │
      ▼
rateio pelo PESOBRUTO
      │
      ▼
conferência do saldo
      │
      ▼
gravação dos vínculos no Sankhya
```

## Funcionalidades

- consulta da nota de frete pelo contexto da tela;
- busca de vendas em janela configurada ao redor da negociação;
- filtros por intervalo de número da nota;
- paginação e seleção em massa;
- resumo de quantidade, peso, valor rateado e saldo;
- cálculo automático do percentual e valor de cada venda;
- ajuste da última linha para eliminar diferenças de arredondamento;
- gravação e exclusão por serviços nativos do Sankhya;
- job agendado para leitura dos XMLs de CT-e;
- rateio de CT-e entre as NF-e referenciadas.

## Arquitetura

| Diretório | Responsabilidade |
| --- | --- |
| `vc/` | Tela HTML5/AngularJS executada dentro do Sankhya Om |
| `model/` | Job Java para sincronização e rateio de CT-e |
| `datadictionary/` | Tabelas, campos e metadados do add-on |
| `dbscripts/` | Orientações sobre scripts e Auto DDL |
| `gradle/` | Wrapper e configuração do Add-on Studio |

### Estruturas de dados

- `TFV_VINCULO_FRETE_VENDA`: vínculo e valor distribuído entre frete e venda;
- `TFV_CTE`: informações consolidadas do CT-e;
- `TFV_CTE_NFE`: índice de documentos relacionados;
- `TFV_CTE_NFE_RATEIO`: peso e valor do CT-e distribuído por NF-e.

## Requisitos

- ambiente Sankhya Om compatível com o add-on;
- JDK 8 de 64 bits;
- Sankhya Add-on Studio e acesso ao repositório de parceiros;
- AppKey e licença configuradas no ambiente;
- banco Oracle compatível com as consultas do job.

O `build.gradle` declara plataforma mínima `4.28`. A implantação deve ser validada na versão exata utilizada pelo ambiente de homologação antes da publicação.

## Configuração local

Credenciais, AppKey e chave privada nunca devem ser versionadas.

```powershell
$env:SANKHYA_APP_KEY = "..."
$env:SANKHYA_PARCEIRO_NOME = "..."
```

## Build

```powershell
$env:JAVA_HOME = "C:\caminho\para\jdk8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat clean gerarAddon `
  --rerun-tasks `
  --no-daemon `
  --console=plain `
  -x processDashboards
```

O pacote é gerado em `build/libs/`.

## Publicação

A publicação exige credenciais do Portal do Desenvolvedor e a chave privada local. Esses valores devem ser fornecidos somente no ambiente autorizado.

```powershell
.\gradlew.bat publishAddon `
  --no-daemon `
  --console=plain `
  -x processDashboards `
  -Pemail="$env:SANKHYA_DEV_EMAIL" `
  -Ppassword="$env:SANKHYA_DEV_PASSWORD" `
  -PprivateKey="C:\caminho\sign_key.key"
```

## Validação funcional

Antes de publicar uma versão, valide em homologação:

1. carregamento da nota de frete pelo contexto;
2. bloqueio após baixa ou contabilização;
3. filtros, paginação e seleção das vendas;
4. rateio por peso com saldo final zerado;
5. gravação, consulta e exclusão dos vínculos;
6. leitura dos XMLs de CT-e pelo job;
7. distribuição do CT-e entre todas as NF-e relacionadas.

## Segurança

- nenhuma credencial, AppKey ou chave privada faz parte do repositório;
- o frontend utiliza serviços autenticados do próprio Sankhya;
- as alterações financeiras devem ser testadas em homologação;
- o job registra falhas sem expor o conteúdo integral dos documentos fiscais;
- a distribuição e o uso dependem das autorizações da Telemassas e das licenças Sankhya.

## Autor e contexto

Solução desenvolvida para a **Telemassas Comércio de Alimentos**, com implementação e manutenção conduzidas por [Leandro Santos](https://github.com/LeandroSatsuki).
