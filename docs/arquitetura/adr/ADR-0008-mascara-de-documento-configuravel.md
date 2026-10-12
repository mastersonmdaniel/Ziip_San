# ADR-0008 — Máscara de CPF/CNPJ configurável com teto validado e busca por documento controlada

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17, achado S1 (§4.2 e §9)

## Contexto

A máscara do CPF mostra 6 dos 9 dígitos-base:
`shared/sigsan-core/src/main/java/br/com/sigsan/core/document/DocumentNumber.java:95` gera `***.456.789-**`. Os dois
dígitos verificadores são calculáveis a partir dos nove primeiros, então restam 3 dígitos desconhecidos, ou seja, no
máximo 1.000 candidatos.

A busca livre por nome ou documento (`POST /customers/search`,
`commercial/sigsan-registry/src/main/java/br/com/sigsan/commercial/registry/web/CustomerController.java:55-58`, e a
equivalente de unidades consumidoras) está aberta a qualquer papel de leitura
(`commercial/sigsan-registry/src/main/java/br/com/sigsan/commercial/registry/web/RegistryRoles.java:12`), sem cota nem
registro. Quem só tem `VIEWER` pode testar os 1.000 candidatos e reconstruir o CPF completo de qualquer titular.

A máscara é calculada na gravação e guardada junto do valor cifrado
(`CustomerDocumentService.java:72`, `MtrDocumentProtection.java:74,99`), e também é usada no PDF do MTR
(`MtrPdfGenerator.java:241`). Os registros existentes já carregam a máscara com 6 dígitos.

Clientes diferentes pedem formatos diferentes de exibição (por exemplo, finais do documento no atendimento telefônico).

## Decisão

1. **Padrão de máscara configurável por tenant**, com padrão de fábrica seguro: **no máximo 3 dígitos-base visíveis do
   CPF** (exemplo: `***.***.789-**`). CNPJ mantém o padrão atual (só a ordem do estabelecimento) até a definição do teto
   próprio, que considera o caso do empresário individual.
2. **Teto validado pelo sistema.** A configuração da máscara não pode expor dígitos suficientes para reconstrução
   combinada com a busca. O sistema recusa qualquer padrão que:
   - mostre mais de 3 dígitos-base do CPF (ou seja, deixe menos de 6 desconhecidos, menos de 1 milhão de candidatos);
   - mostre dígito verificador (cada um reduz o espaço de busca em cerca de 10 vezes).
   Subir o teto exige novo ADR.
3. **Em qualquer configuração, a busca por documento:**
   - exige **papel próprio** (por exemplo, `registry.search-document`), separado de leitura e de revelação;
   - aceita só documento **completo e válido** (sem busca parcial ou por prefixo);
   - é **registrada na auditoria** (`personal_data_access_log`), com usuário, tenant, finalidade e resultado, sem o
     documento em claro (hash ou índice cego);
   - tem **cota por usuário por hora**, com recusa ao exceder;
   - dispara **alerta de volume anômalo** por usuário e por tenant.
4. A máscara exibida em telas, listas, exportações e PDFs vem sempre da política do tenant; nenhum ponto do sistema
   monta máscara própria.

## Consequências

### Positivas

- Fecha o caminho de reconstrução do S1: com 6 dígitos desconhecidos e cota por hora, a enumeração deixa de ser
  viável e, se tentada, aparece no alerta.
- O tenant ajusta a exibição ao seu atendimento sem poder ultrapassar o limite seguro.
- Toda busca por documento passa a ter rastro, o que atende pedido do titular e investigação de incidente.

### Negativas

- Atendimento que hoje identifica o cliente pelos 6 dígitos do meio perde essa referência; precisa usar matrícula,
  nome ou o papel de busca por documento.
- A mudança de política por tenant exige recalcular as máscaras guardadas, o que implica decifrar em lote (no worker,
  com auditoria).
- Cota mal dimensionada atrapalha operações legítimas de volume (conferência de importação); essas usam rotina própria,
  não a busca interativa.

### O que muda no código

- `DocumentNumber.mask(String)` passa a receber a política do tenant e a validá-la contra o teto; a validação também
  roda ao salvar a configuração.
- Política de máscara como parâmetro do tenant (ADR-0002), com padrão de fábrica.
- Migração de dados: recálculo das máscaras já gravadas (cadastro e MTR) para o padrão novo. Verificar também se o
  `audit_log` guarda a máscara antiga nas colunas não redigidas.
- `CustomerService.search` e a busca de unidades consumidoras separam o caminho por documento, com papel, registro,
  cota e alerta; o caminho por nome continua com o papel de leitura.
- Teste de propriedade: para toda política aceita, restam pelo menos 6 dígitos-base desconhecidos e nenhum dígito
  verificador visível.

### O que muda no roadmap

- Entra no P0 (correção S1, já listada na seção 9 da revisão 17). A configuração por tenant aparece no console da
  plataforma v0 (P1).

## Alternativas consideradas

- **Máscara fixa no produto, sem configuração.** Rejeitada: não atende formatos de atendimento diferentes entre
  clientes.
- **Máscara livre por tenant.** Rejeitada: permite voltar ao estado do S1 por configuração.
- **Só restringir a busca, mantendo 6 dígitos visíveis.** Rejeitada: com 1.000 candidatos, mesmo uma cota baixa
  permite reconstruir um CPF específico em poucos dias.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Padrão de fábrica com 3 dígitos, teto validado e teste de propriedade | P0 |
| Papel de busca por documento, busca só por documento completo, registro, cota e alerta | P0 |
| Recálculo das máscaras já gravadas e conferência do `audit_log` | P0 |
| Teto de máscara do CNPJ, considerando empresário individual | P0 |
| Política de máscara por tenant no console da plataforma v0, com recálculo em lote | P1 |
