# Classificação de dados pessoais (P0.11)

Catálogo `public.data_classification`: uma linha por tabela e coluna com dado pessoal,
com a classe e o motivo. Fonte: `platform/ziipsan-platform/.../db/migration/tenant/`
(`V1_6` cria a tabela; `R__data_classification.sql` semeia a partir das regras de
redação — a repetível roda depois de todas as versionadas e por isso enxerga as regras
de todos os módulos).

## Classes

| Classe | Significado | Exemplos |
|---|---|---|
| `ID` | Identificação direta ou indireta | nome, CPF/CNPJ, índice cego, cifra, máscara, assinatura, registro profissional, texto livre que pode conter dado pessoal |
| `CT` | Contato | e-mail, telefone |
| `LOC` | Localização | endereço, CEP, coordenadas |
| `FIN` | Financeiro | (nenhuma coluna classificada ainda) |
| `SOC` | Social/demográfico | (nenhuma coluna classificada ainda) |
| `SENS` | Sensível (LGPD art. 11) | fotos que podem mostrar pessoas |

FIN e SOC existem no CHECK mas ainda não têm coluna: nenhuma regra de redação atual é
financeira ou social, e inventar uma seria fabricar dado.

## Regras

- Toda coluna com nome de dado pessoal numa tabela auditada precisa de classificação:
  o teste `DataClassificationCoverageTest` recusa coluna nova sem classificação (mesmo
  padrão e allowlist de `AuditRedactionCoverageTest`, em `PersonalDataColumns`).
- Toda coluna com regra de redação tem classificação: o seed deriva a classe da regra.
- Uma tarefa futura que crie coluna com dado pessoal classifica a coluna na própria
  migration (ou atualizando a repetível): o teste quebra o build até lá.
- `consumer_units.coordinates` é `LOC` (P0.11): a isenção antiga foi revista — a
  localização da ligação vinculada a pessoa é dado pessoal. Continuam isentas (não são
  dado pessoal): `sampling_point.coordinates` (ponto público) e
  `waste_destination.coordinates` (pessoa jurídica).

## Registro de acesso

`personal_data_access_log` (V1_6, um registro por tenant) é a base da detecção de
anomalia (ADR-0008, RNF-41), cuja detecção completa é da P3.10. Aqui: buscas por
documento (com a permissão `registry:search-document` e a cota por usuário por hora),
leituras de cadastro e revelações — sempre sem o documento em claro (no máximo o
índice cego). A tabela não tem trigger de auditoria (o log não se audita a si mesmo)
e por isso está em `NOT_AUDITED_BY_DESIGN` no `FreshInstallTest`.
