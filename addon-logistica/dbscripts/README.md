# Migração da LOGISTICA 1.0.43

O projeto mantém autoDDL=false. V1_historico_vinculo.xml cria somente LGC_HIST_VINCULO por DBScripts oficial, SE_NAO_EXISTIR. Não existe script de migração de dados ou alteração das tabelas TFV/nativas. Testar instalação e compatibilidade da tabela na homologação antes de permitir gravação. A auditoria é obrigatória: a ação falha e reverte caso não consiga gravar o histórico.

A estrutura de auditoria não deve ser apagada no rollback operacional. Não desinstalar o addon-template nem reimportar seu dicionário.
