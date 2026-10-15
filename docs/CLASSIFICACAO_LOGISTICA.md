# Classificação logística por nota de venda

## Decisão técnica

A classificação foi armazenada em `SANKHYA.TFV_CLASSIFICACAO_LOGISTICA`, com uma única linha por `NUNOTA`.
Ela não foi incluída em `TFV_VINCULO_FRETE_VENDA`, porque uma nota pode participar de mais de um vínculo de frete e a informação logística pertence à nota.

Não foi criada chave estrangeira para `TGFCAB`. O add-on atual não cria FKs contra tabelas nativas e essa restrição poderia bloquear uma rotina nativa de exclusão de notas. O backend valida a existência da `NUNOTA` antes de inserir ou atualizar a classificação. A chave primária de `TFV_NUNOTA` garante a unicidade.

## Contrato persistente

| Campo | Tipo Oracle | Regra |
|---|---|---|
| `TFV_NUNOTA` | `NUMBER` | Chave primária; NUNOTA da venda |
| `TFV_TIPO_CARREGAMENTO` | `VARCHAR2(1)` | `N` = movimentação normal; `I` = direto da indústria |
| `TFV_CODUSU` | `NUMBER` | Usuário da última alteração |
| `TFV_DHALTER` | `DATE` | Data e hora da última alteração |

Notas sem registro são interpretadas como `N` na interface e deverão ser tratadas como `N` pelos consumidores com `NVL`.

Consulta de integração:

```sql
SELECT
    TFV_NUNOTA,
    TFV_TIPO_CARREGAMENTO,
    TFV_CODUSU,
    TFV_DHALTER
FROM SANKHYA.TFV_CLASSIFICACAO_LOGISTICA
WHERE TFV_NUNOTA = :NUNOTA;
```

Regra futura do relatório de margem, não aplicada neste add-on:

```sql
CASE
    WHEN NVL(CL.TFV_TIPO_CARREGAMENTO, 'N') = 'I' THEN 0
    ELSE PESO_TOTAL_NOTA * 0.35
END
```

## Serviço

O endpoint transacional é:

```text
/addon-template/service.sbr?serviceName=TFVRateioFreteSP.salvarRateio&outputType=json
```

Exemplo de gravação do rateio e das classificações na mesma transação:

```json
{
  "serviceName": "TFVRateioFreteSP.salvarRateio",
  "requestBody": {
    "rateio": {
      "nunotaFrete": 30020,
      "vendas": [
        {
          "nunota": 28271,
          "valorRateio": 2500.00,
          "percentual": 50.00,
          "tipoCarregamento": "N"
        },
        {
          "nunota": 28371,
          "valorRateio": 2500.00,
          "percentual": 50.00,
          "tipoCarregamento": "I"
        }
      ]
    }
  }
}
```

Para alterar somente a classificação de vínculos já concluídos:

```text
/addon-template/service.sbr?serviceName=TFVRateioFreteSP.salvarClassificacoes&outputType=json
```

```json
{
  "serviceName": "TFVRateioFreteSP.salvarClassificacoes",
  "requestBody": {
    "request": {
      "vendas": [
        { "nunota": 28371, "tipoCarregamento": "N" }
      ]
    }
  }
}
```

A leitura é feita pela entidade `TFV_CLASSIFICACAO_LOGISTICA` em lotes de NUNOTAs. Ausência de linha resulta em `N` apenas em memória; abrir a tela não cria registros.

## Transação e auditoria

- `TFVRateioFreteSP` usa transação EJB `Required`.
- O vínculo é gravado com `MERGE`, preservando a chave composta atual e permitindo repetição idempotente.
- A classificação é gravada com `MERGE` por `TFV_NUNOTA`.
- `TFV_CODUSU` recebe o usuário autenticado e `TFV_DHALTER` recebe `SYSDATE`.
- Se qualquer vínculo ou classificação falhar, o serviço lança erro e a transação inteira é revertida.
- Excluir ou refazer um vínculo não remove a classificação logística.

## Validações SQL pós-instalação

```sql
-- Conteúdo de uma nota
SELECT TFV_NUNOTA, TFV_TIPO_CARREGAMENTO, TFV_CODUSU, TFV_DHALTER
FROM SANKHYA.TFV_CLASSIFICACAO_LOGISTICA
WHERE TFV_NUNOTA = :NUNOTA;

-- Não deve retornar linhas duplicadas
SELECT TFV_NUNOTA, COUNT(*) AS QTD
FROM SANKHYA.TFV_CLASSIFICACAO_LOGISTICA
GROUP BY TFV_NUNOTA
HAVING COUNT(*) > 1;

-- Não deve retornar tipos inválidos
SELECT *
FROM SANKHYA.TFV_CLASSIFICACAO_LOGISTICA
WHERE TFV_TIPO_CARREGAMENTO NOT IN ('N', 'I')
   OR TFV_TIPO_CARREGAMENTO IS NULL;

-- Confirma que carga direta não altera nem zera o rateio
SELECT V.TFV_NUNOTA_FRETE,
       V.TFV_NUNOTA_VENDA,
       V.TFV_VLR_RATEIO,
       CL.TFV_TIPO_CARREGAMENTO
FROM SANKHYA.TFV_VINCULO_FRETE_VENDA V
LEFT JOIN SANKHYA.TFV_CLASSIFICACAO_LOGISTICA CL
       ON CL.TFV_NUNOTA = V.TFV_NUNOTA_VENDA
WHERE V.TFV_NUNOTA_FRETE = :NUNOTA_FRETE;
```

## Roteiro obrigatório de homologação

| Caso | Procedimento | Resultado esperado |
|---|---|---|
| Movimentação normal | Concluir uma nota com `N` | Uma linha por NUNOTA com tipo `N` |
| Direto da indústria | Concluir uma nota com `I` | Tipo `I` e aviso visual na linha |
| Classificações diferentes | Ratear duas ou mais notas usando `N` e `I` | Cada NUNOTA mantém seu próprio tipo |
| Reabertura | Fechar e reabrir o rateio | Valores anteriores reaparecem |
| Alteração I → N | Alterar na grade vinculada e salvar | Mesmo registro passa a `N`; auditoria atualizada |
| Alteração N → I | Alterar na grade vinculada e salvar | Mesmo registro passa a `I`; auditoria atualizada |
| Carga direta com frete | Concluir `I` com rateio maior que zero | Rateio e CT-e continuam inalterados e maiores que zero |
| Nota antiga | Abrir nota sem classificação | Interface mostra `N`, sem inserir linha automaticamente |
| Repetição | Enviar novamente o mesmo payload | Nenhuma duplicidade; a linha existente é atualizada |
| Erro de gravação | Simular falha na tabela de classificação | Mensagem clara; nenhum vínculo parcial é confirmado |
| Refazer vínculo | Excluir o vínculo e criá-lo novamente | Classificação permanece pela NUNOTA |

## Evidências técnicas executadas

- `node --check` validou a sintaxe do JavaScript.
- O metadado XML da nova entidade foi processado pelo gerador do add-on.
- `gradlew clean build` concluiu com sucesso usando Java 8 x64.
- O pacote gerado contém o provedor `TFVRateioFreteSP` e a transação `Required`.

Os cenários funcionais do roteiro exigem a instalação do artefato em uma instância Sankhya e devem ser registrados durante a homologação.
