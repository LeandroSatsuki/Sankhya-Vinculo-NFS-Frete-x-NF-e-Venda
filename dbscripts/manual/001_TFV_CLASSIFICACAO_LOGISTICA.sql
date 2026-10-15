-- Contrato persistente da classificação logística por nota de venda.
-- O add-on usa autoDDL=true e cria a tabela pelo arquivo de metadados.
-- Este script separado documenta o DDL equivalente para instalação/validação manual.
-- Não há alteração em tabelas existentes.

CREATE TABLE SANKHYA.TFV_CLASSIFICACAO_LOGISTICA (
    TFV_NUNOTA              NUMBER       NOT NULL,
    TFV_TIPO_CARREGAMENTO   VARCHAR2(1)  DEFAULT 'N' NOT NULL,
    TFV_CODUSU              NUMBER       NOT NULL,
    TFV_DHALTER             DATE         NOT NULL,
    CONSTRAINT PK_TFV_CLASSIFICACAO_LOGISTICA
        PRIMARY KEY (TFV_NUNOTA),
    CONSTRAINT CK_TFV_CLASS_LOG_TIPO
        CHECK (TFV_TIPO_CARREGAMENTO IN ('N', 'I'))
);

COMMENT ON TABLE SANKHYA.TFV_CLASSIFICACAO_LOGISTICA IS
    'Classificação logística única por nota de venda';
COMMENT ON COLUMN SANKHYA.TFV_CLASSIFICACAO_LOGISTICA.TFV_NUNOTA IS
    'Número único da nota de venda';
COMMENT ON COLUMN SANKHYA.TFV_CLASSIFICACAO_LOGISTICA.TFV_TIPO_CARREGAMENTO IS
    'N = Movimentação normal; I = Carga direta da indústria';
COMMENT ON COLUMN SANKHYA.TFV_CLASSIFICACAO_LOGISTICA.TFV_CODUSU IS
    'Código do usuário da última alteração';
COMMENT ON COLUMN SANKHYA.TFV_CLASSIFICACAO_LOGISTICA.TFV_DHALTER IS
    'Data e hora da última alteração';

-- A chave primária já cria o índice único necessário para garantir uma linha por NUNOTA.
-- Não foi criada FK para TGFCAB porque o projeto não usa FKs para tabelas nativas e a
-- restrição poderia bloquear rotinas nativas de exclusão. O serviço valida a existência
-- da NUNOTA antes de persistir.
