# Documentação das customizações do cliente — ZiipSan (ED-11)

Toda implantação deixa o tenant um pouco diferente do produto padrão.
Este documento define o registro dessas diferenças: o que conta como
customização, o que o registro de cada uma precisa ter e quando a
documentação está completa. A régua está em `CustomizationDoc`
(produto) e a persistência, em `platform.tenant_customization` (SQL
pendente da ED-11). O modelo de lançamento é
`REGISTRO-CUSTOMIZACOES.template.csv`.

## O que é customização (só os pontos que o plano prevê)

| Categoria | O que entra | Origem |
|---|---|---|
| `PARAMETER` | valor de parâmetro do tenant divergente do padrão de fábrica, sempre por versão aceita | P0.19 |
| `BRAND_THEME` | marca do tenant, temas e marca no portal e nos e-mails | P1.19, P0.34–P0.38, P3.21 |
| `ACCESS_PROFILE` | perfil de acesso próprio composto pelo administrador do tenant | ED-06, P0.13, P1.37 |
| `LEGACY_MAPPING` | arquivo de mapeamento do legado do cliente no framework declarativo de migração | P1.2 (PLT-16) |
| `DOCUMENT_TEMPLATE` | modelo de documento ou de mensagem específico do tenant no motor único | P2.30, P2.32 |
| `INTEGRATION` | conector ou integração específica ligada no hub | P2.26, P3.37 |
| `VERTICAL_MANIFEST` | conteúdo do manifesto do pacote de vertical do tenant | P0.47, ADR-0037 |

Configuração que é do produto para todos (flag de plataforma, plano
contratado) não é customização e não entra aqui. Funcionalidade nova
pedida pelo cliente também não: ela é demanda de produto, não registro
de implantação — registrá-la aqui a disfarçaria de variação aceita.

## O registro de cada customização

- **chave** estável dentro do tenant e **categoria** do domínio acima;
- **título e descrição** do que diverge e por quê (a razão é parte do
  registro: customização sem razão registrada é a primeira a virar
  mistério na reversão);
- **origem** (`sourceRef`): a tarefa/parâmetro/mecanismo do plano que
  sustenta a variação;
- **situação**: `RECORDED` (levantada), `PENDING_ACCEPTANCE` (aguarda o
  tenant) ou `ACCEPTED`;
- quando aceita: **quem decidiu** (papel/identificação do decisor no
  tenant), **quando**, e a **nota de reversão** — como aquilo se desfaz
  (restaurar o padrão, desativar o perfil, remover o mapeamento), que é
  o que o pacote de reversão da ED-04 consome.

## Quando a documentação está completa

- nenhum item fora de `ACCEPTED`;
- todo item aceito com decisão (quem/quando) e nota de reversão;
- nenhuma chave repetida;
- e, se o tenant não tem customização nenhuma, a declaração explícita
  disso (`declaredNone`) — registro vazio em silêncio não é
  documentação: é ausência dela.

## As customizações concretas de um cliente

Elas nascem na implantação daquele cliente e não existem antes dela:
**[A DEFINIR COM O CLIENTE: customizações concretas de cada tenant,
registradas durante a sua implantação neste modelo]**. Este pacote
entrega o domínio, a régua, o modelo e a persistência — não inventa
customização de cliente nenhum.
