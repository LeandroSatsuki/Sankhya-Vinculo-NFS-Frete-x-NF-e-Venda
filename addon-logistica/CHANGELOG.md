# LOGISTICA

## 1.0.45 — entrega de 19/10/2026

- Autorização da manutenção pelo gerenciador nativo do recurso, com permissões de consultar, alterar e configurar separadas.
- Estado contábil via `TCBINT` e baixa via `ComercialUtils`; erro ou estado desconhecido bloqueia a manutenção.
- Mesma fonte de estado para tela contextual e manutenção, com sessão JAPE antes das consultas de autorização.
- Inativação auditada e controles contra concorrência, saneamento de órfãos com confirmação e reativação automática.
- Preservado o job histórico com fechamento centesimal e a dependência das entidades TFV do original.

Importação no Git em 24/11/2026 a partir dos fontes preservados da entrega. Não houve novo deploy nem alteração funcional nesta importação. Consulte [proveniência](../docs/VERSOES.md).
