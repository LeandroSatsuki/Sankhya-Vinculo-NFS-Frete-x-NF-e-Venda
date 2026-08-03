# Vínculo NFS-e de frete x NF-e de venda

Add-on para o Sankhya Om que permite selecionar notas de venda e distribuir o valor de uma nota de serviço de frete proporcionalmente ao peso bruto (`PESOBRUTO`).

## O que o projeto entrega

- Consulta da nota de serviço e bloqueio quando há parcela baixada ou contabilização.
- Seleção de vendas de qualquer empresa em uma janela de 60 dias antes e 15 dias depois da negociação do frete.
- Filtros por número da nota inicial e final, paginação, seleção em massa e resumo de quantidade e peso.
- Rateio automático por peso bruto, com fechamento exato do valor do frete.
- Gravação dos vínculos na tabela `TFV_VINCULO_FRETE_VENDA`.
- Exclusão física de vínculos pela própria tela, permitindo refazer o rateio.
- Rateio de CTE por peso entre várias NFes, com suporte a CTE principal, secundário e demais CTEs.
- Tabelas `TFV_CTE` e `TFV_CTE_NFE_RATEIO` para armazenar o CTE e o rateio por NF-e.
- Job agendado para sincronizar CTEs a partir de 01/07/2026.

## Estrutura

| Diretório | Responsabilidade |
| --- | --- |
| `vc/` | Tela HTML5 e interação do usuário |
| `model/` | Jobs e componentes Java |
| `datadictionary/` | Tabelas e instâncias do Dicionário de Dados |
| `dbscripts/` | Scripts auxiliares/migrações |
| `gradle/` | Gradle Wrapper |

## Requisitos

- Sankhya Om 4.36b110 ou superior;
- JDK 8 (Temurin recomendado);
- AppKey e código de licença configurados no ambiente de desenvolvimento;
- acesso à Área do Desenvolvedor Sankhya.

## Configuração local

Não versione credenciais, `APPKEY`, senha, chave privada ou arquivos `.env`. Configure as variáveis somente no ambiente local:

```powershell
$env:SANKHYA_APP_KEY = "..."
$env:SANKHYA_PARCEIRO_NOME = "..."
```

O arquivo `build.gradle` lê a AppKey por variável de ambiente.

## Build

```powershell
$jdk = "C:\Users\<usuario>\.jdks\temurin8u492-x64"
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

.\gradlew.bat clean gerarAddon `
  --rerun-tasks `
  --no-daemon `
  --console=plain `
  -x processDashboards
```

O pacote é gerado em `build/libs/addon-template.exts`.

## Publicação

Para publicar na Área do Desenvolvedor, informe as credenciais fora do repositório e use a chave privada local:

```powershell
.\gradlew.bat publishAddon `
  --no-daemon `
  --console=plain `
  -x processDashboards `
  -Pemail="$env:SANKHYA_DEV_EMAIL" `
  -Ppassword="$env:SANKHYA_DEV_PASSWORD" `
  -PprivateKey="C:\caminho\sign_key.key"
```

Após a publicação, a versão deve ser promovida no Portal do Desenvolvedor e instalada pela tela **Minhas Soluções** do Sankhya Om.

## Dados e segurança

As tabelas usam o prefixo `TFV` para reduzir conflitos com outros add-ons:

- `TFV_VINCULO_FRETE_VENDA`: vínculos e valores rateados;
- `TFV_CTE_NFE`: índice de valores de CTE por chave da NF-e.

O projeto não contém credenciais, chaves privadas, dumps de banco ou artefatos gerados.

## Validação funcional

Antes de publicar uma versão, valide em homologação:

1. nota de frete sem baixa e sem contabilização;
2. seleção de vendas e rateio por peso;
3. saldo final igual a zero;
4. gravação e consulta dos registros em `TFV_VINCULO_FRETE_VENDA`;
5. exclusão de vínculo e novo rateio;
6. bloqueio após baixa/contabilização;
7. preenchimento de `TFV_CTE_NFE` pelo job agendado.

## Licença

Projeto interno da Telemassas. Uso e distribuição dependem da autorização da empresa e das licenças do Sankhya Om.
