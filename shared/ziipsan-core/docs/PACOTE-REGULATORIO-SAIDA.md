# ED-97 — Pacote regulatório de saída por agência

Camada de exportação parametrizada por agência sobre o perfil regulatório
da P2.31 (RAS-01), no modo assistido do ADR-0004 (`reg.reports`, classe d,
REG): o sistema gera, valida e registra o arquivo com hash; o tenant envia.

## Conteúdo (GES-04)
Consumo médio por categoria e faixa; receita e usuários subsidiados; custo
do subsídio tarifário, fiscal ou interno (Lei 11.445/2007, art. 31);
contratos de grandes usuários; taxa de inadimplência por categoria;
relatório anual de beneficiários da tarifa social.

## Tarifa social (CAD-92)
ITS mensal = economias residenciais beneficiadas / economias ativas.
Reconciliação mensal com a lista positiva da ANA por município e regulador;
relatório anual à agência (NR 13/ANA, art. 45; `social.annual_report`).

## Perfis de fábrica (RAS-08/RAS-31)
ARIS (SISARIS por módulo — IN DG 1/2025, RN 11/2017), AGIR (trimestral
SAC/ouvidoria — RN 013/2022) e ARESC. Os textos dos leiautes oficiais não
foram localizados nas fontes do projeto: campos `[SEM FONTE]`,
`layoutComplete=false`, pendências P-ED97-01/P-ED97-02 — nenhum leiaute
inventado é apresentado como oficial. Agência fora de SC: sem perfil de
fábrica; valores próprios aceitos pelo tenant (RAS-31).

## Separação e escopo externo
Exportação agregada pública nunca carrega linha nominal; nominal é sempre
restrita. O escopo regulatório do perfil externo do regulador (ED-75,
P-ED75-01 atendida aqui): este pacote, indicadores da ED-48, relatório do
art. 54 da NR 11, ouvidoria e interrupções — sem evidência ISO, sem dado
pessoal. Correção R7-B1 (Rodada 7): o item "este pacote" no escopo acima
denota somente a parte agregada pública — o pacote nominal restrito não
tem recurso externo (`RegulatorExternalScope.resourceForPackage` devolve
vazio e `canRegulatorReadPackage` é falso para ele) e a evidência
externa (`ExportSubmissionService.evidenceRefsFor(periodo)`) devolve
somente refs agregadas; refs nominais só por kind explícito, em uso local.
