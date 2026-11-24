# Manutenção deste repositório

- Há dois projetos Gradle independentes: original 1.0.39 na raiz e LOGISTICA em `addon-logistica/`. Consulte `docs/VERSOES.md` antes de diagnosticar ou alterar qualquer um deles.
- A LOGISTICA complementa o original e compartilha as tabelas TFV; não substituir serviços/contextos nem recriar as tabelas do original por suposição.
- Antes de uma entrega, versionar fontes, testes e migrações; atualizar `releases/index.json`, criar o manifesto da nova versão e registrar os hashes de fonte/pacote. Nunca reutilizar uma versão para um binário diferente.
- Executar `node scripts/verify-release.mjs` e os testes do projeto alterado. A configuração de CI não instala nem publica no ERP.
- Separar fonte, pacote gerado, instalação e homologação. Coincidência de número de versão não comprova igualdade binária; conferir hashes quando o pacote estiver disponível.
- Preservar os bytes dos fontes importados da 1.0.45. Mudanças produtivas futuras exigem nova versão/manifesto; não atualizar hashes históricos silenciosamente.
- Não versionar credenciais, certificados, pacotes licenciados, logs, capturas de ERP, consultas com resultados reais ou dados de clientes. Artifacts de entrega ficam em armazenamento privado; o Git contém seus hashes.
- Nesta importação não há autorização para alterar regras de negócio, instalar, publicar ou executar DML no Sankhya. Cada pedido futuro deve definir seu próprio escopo.
