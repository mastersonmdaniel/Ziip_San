# Mapa de cláusulas por edição — ED-76

Fonte de curadoria do mapa de cláusulas que estende o catálogo-base
deste diretório (`catalogo-normas.json`, carregado pela P1.34).

## Formato do arquivo: envelope + mapas

O arquivo `mapa-clausulas.json` é um **envelope**, não um mapa solto:

```json
{"mapas": [], "versaoFormato": 1}
```

Quem carrega o arquivo é `ClauseCuration.fromJsonEnvelope` (core,
pacote `br.com.ziipsan.core.certification`): ele valida as chaves
fechadas do envelope (`mapas`, `versaoFormato`), exige
`versaoFormato` igual a 1 e desdobra cada elemento de `mapas` como um
mapa individual, pela mesma leitura de `ClauseCuration.fromJson` —
com a mesma lista fechada de chaves nos três níveis (envelope, mapa,
cláusula). A escrita do envelope é `ClauseCuration.toJsonEnvelope`.
O envelope com a lista vazia é legível por esse caminho e devolve
lista vazia.

Cada mapa dentro do envelope tem o formato:

```json
{
  "standardCode": "<code da norma no catálogo>",
  "edition": "<edição>",
  "clauses": [
    {
      "clauseNumber": "<número da cláusula>",
      "title": "<título escrito em palavras próprias pela curadoria>",
      "summary": "<resumo em palavras próprias, ou null>",
      "sortOrder": 1
    }
  ]
}
```

`standardCode` é o **`code`** de `platform.cert_standard` (V9_8) — a
chave que o `CertificationService` usa para achar a norma e que a
ativação grava como `standard_code`. **Não** é o `catalog_key`: as
duas colunas são UNIQUE distintas, e a carga SQL abaixo resolve a
edição por `code`, pela mesma razão.

Regras (ADR-0041, item 11; P1.28):

- o produto guarda **só título, edição e número de cláusula** — e o
  resumo em palavras próprias da curadoria. **Nunca** texto da norma:
  os textos comprados ficam fora do repositório, conforme a licença;
- qualquer campo fora das listas fechadas acima é recusado pela
  leitura (`ClauseCuration`): a trava barra um campo novo/dedicado
  para texto de norma entrar sem revisão. Limite honesto da trava:
  `title` e `summary` são texto livre (como na V9_8), então ela **não**
  detecta texto de norma copiado e colado dentro do resumo — o
  controle sobre o conteúdo desses campos é o processo de curadoria
  humana, não o código;
- a carga no catálogo global é a migration repetível
  `R__norm_clause_map.sql` (core), por upsert em
  `platform.cert_clause` (tabela da V9_8/P1.32), resolvendo a edição
  por `s.code` + `edition`. Antes de aplicar um bloco real, a
  conferência obrigatória descrita no cabeçalho da `R__` precisa
  devolver exatamente 1 linha — `INSERT … SELECT` com zero linhas é
  falha silenciosa, não sucesso;
- a cada nova edição, a revisão compara o mapa anterior com o novo
  (`ClauseMapRevision`) e gera a lista de evidências a revalidar nos
  tenants que usam a norma (`EvidenceRevalidation`).

Estado atual: **lista de mapas vazia, deliberadamente.** Nenhum mapa
real foi escrito nesta tarefa porque o conteúdo só pode nascer dos
textos comprados (P1.28), que não estão no repositório. Escrever
cláusulas de memória seria fabricar conteúdo de norma.
