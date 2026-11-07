# Revisão cruzada da P2.32 (autorrevisão declarada)

Limite de ambiente: esta frente roda em depth 2/2 sem permissão de spawn —
não foi possível acionar um subagente revisor separado. A revisão foi feita
pelo próprio autor contra a spec, item a item, e está registrada aqui com a
mesma franqueza das revisões anteriores do projeto; a revisão cruzada por
agente distinto deve ser refeita pelo agente principal antes do push.

## Achados e correções
1. [MÉDIO] Carga R__ sem chave de upsert duplicaria linhas a cada reexecução.
   Corrigido: V4_10 ganhou índices únicos de chave estável
   (uq_invoice_required_fields_key, uq_legal_message_catalog_key).
2. [BAIXO] Contagem da R__ de conteúdo: 5 federais (profile_key NULL, uma vez)
   + 11 de perfil = 16 linhas — conferida à mão e travada em comentário +
   teste de consistência.
3. [BAIXO] Perfis AGR-Tubarão e CISAM-SUL sem campos de ERI: decisão
   deliberada (texto não confirmado na P2.31), testada — não é omissão.
4. [OBSERVAÇÃO, sem correção nesta frente] TemplateEngine da P2.30 recusa
   blocos SYSTEM vindos do tenant; a fiação que aceita os SYSTEM da ponte
   LegalTemplateBlocks é ponto de integração declarado no relatório.

## Conferido sem achado
- Nenhuma asserção afrouxada (pacote novo; nenhum teste existente tocado).
- JSON: nenhuma classe nova serializa JSON à mão (a ponte e o motor são
  puros; Json do core não foi necessário).
- RAS-28: campo ausente do catálogo, da R__ e da V4_10; presença no modelo
  é recusada pela regra de conteúdo (teste dedicado).
- Balanço de chaves/parênteses de todos os .java conferido por script.
