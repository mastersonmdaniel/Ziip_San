# Plano de teste de seguranca — P3.8 (homologacao local)

Repo `mastersonmdaniel/Ziip_San`, HEAD de referencia `0fbcd22`. Ambiente:
o local de homologacao da P0.27 (Docker Compose, dado sintetico —
ADR-0033). Producao e pentest externo sao da LC.8.

## 1. Criterio de aceite de seguranca

OWASP ASVS 5.0, **nivel 2 em geral e nivel 3 em autenticacao, pagamento
e dados pessoais** (cartao do dono de 29/10/2026). Componentes por nivel
(a lista exigida pelo aceite; espelho em `SecurityBaseline.componentLevels()`):

| Nivel | Componentes |
|---|---|
| L3 | Autenticacao (realms Keycloak `ziipsan`/`ziipsan-portal`, login do portal, MFA, gov.br, recuperacao de senha) |
| L3 | Pagamento (Pix/boleto da P3.2, pagamento convidado, devolucao da P3.29) |
| L3 | Dados pessoais (portal do consumidor, vinculo, downloads, `personal_data_access_log`, cifra/indice cego, cofre) |
| L3 | API publica — autenticacao e autorizacao do cliente OAuth (P2.26) |
| L2 | Backoffice e Central Ziip; billing/metering/fiscal; notificacoes, relatorios, GIS; API publica fora autenticacao/autorizacao |

**Portao da rodada: nenhum achado alto (ou critico) aberto** —
`FindingGate` (Java) e `scripts/security/avaliar-achados.py` aplicam a
mesma regra sobre os achados consolidados (ZAP + testes + SBOM/audit).

## 2. Casos (registro executavel em `SecurityCase`)

Vinculo; enumeracao de matricula, de codigo de vinculacao e de
CPF/CNPJ/e-mail no login e na recuperacao de senha; enumeracao do codigo
no caminho de convidado (ADR-0043, item 4); troca de token entre realms;
download do portal; acesso assistido; acesso de emergencia; defesa
contra automacao da P3.3 (PLT-22), **inclusive a ausencia de teste
cognitivo sem alternativa** (WCAG 2.2, 3.3.8: `BotDefensePolicy` so
conhece desafios nao cognitivos — prova por construcao e por teste);
isolamento entre tenants; cofre; DAST autenticada; API publica contra o
OWASP API Security Top 10 2023; A03 (cadeia de suprimentos) e A10
(condicoes excepcionais) do OWASP Top 10:2025.

Cada caso declara, no registro e na matriz
(`matriz-requisito-teste-evidencia.csv`), como executa: unidade (nesta
entrega, contra as regras reais da P3.3/P3.17 e o vinculo da P3.20),
stack no CI, DAST ou criterio de outra tarefa. **Caso sem execucao
declarada nao entra.**

> Nota da matriz (correcao D10): a linha `APPS_LC8` (RNF-39, apps) so
> existe no CSV — e a linha da LC.8, coberta pelo pentest externo, sem
> caso Java local na P3.8. O `validar-matriz.py` cruza todos os demais
> casos CSV x `SecurityCase` (nomes e conjuntos ASVS/OWASP/API) e
> reprova divergencia; `APPS_LC8` e a unica excecao, declarada no
> proprio validador.

## 3. DAST autenticada (OWASP ZAP)

Plano em `zap-plano-autenticado.yaml` (Automation Framework): contextos
autenticados do portal e do backoffice, usuarios **sinteticos** da massa
local com credenciais vindas do ambiente, spider + active scan, relatorio
JSON tradicional. Validador de plano: `ZapScanPlan` (anonima, sem
contexto, usuario nao sintetico ou alvo de producao = plano recusado).
Execucao: `scripts/security/run-dast.sh` — exige a stack local de pe;
nesta VM (sem Docker/JDK) ele nao roda, e nada aqui afirma relatorio ZAP
real.

> Ressalva registrada: os URLs de login e as regexes do plano sao os
> marcadores do ambiente local conhecidos pelas tarefas do portal; se a
> fiacao real do BFF/Keycloak diferir, ajustar o YAML na integracao
> antes da primeira varredura — o plano recusar login que nao autentica
> e o comportamento esperado (sem sessao, sem evidencia).

## 4. Massa e fixtures sinteticas

- Unidade: chaves fixas de teste e documentos marcadores publicos do
  projeto (mesmos da suite da P3.3); relatorios ZAP sinteticos em
  `src/test/resources/security-review/` (um sem altos, um com um alto).
- Stack/DAST: usuarios `sintetico.*@example.invalid` criados na massa
  local da P0.27; **nenhum dado pessoal real** (ADR-0033) e nenhum
  segredo em arquivo (credenciais so por variavel de ambiente).

## 5. Roteiro de execucao (PC do dono / CI)

1. Integrar, nesta ordem: pacotes locais da P3.3, da P3.17 e da P3.20
   (a suite compila contra eles — o teste de vinculo usa o
   `ConsumerLinkService` da P3.20, correcao D5), depois esta entrega.
   Sem migration (a P3.8 nao reserva versao Flyway — conferir o mapa
   do plano §3 na integracao).
2. `scripts/ci.sh` (rodada total da camada) — inclui os testes novos.
3. `scripts/security/run-homologacao-seguranca.sh` com a stack local de
   pe e as variaveis `ZIIPSAN_BASE_URL`, `ZAP_PORTAL_USER/PASS`,
   `ZAP_BACKOFFICE_USER/PASS` (usuarios sinteticos).
4. Anexar ao diario da camada: matriz gerada
   (`SecurityBaseline.matrixJson()` via teste/CI), relatorio ZAP,
   resumo do `FindingGate` e a lista de achados com disposicao.
5. Achado alto aberto = camada nao fecha. Correcao + reteste entram como
   fix da camada, sem afrouxar assercao.

## 6. Criterios objetivos de aprovacao

- Testes da suite verdes no CI (unidade + integracao donas dos casos
  `AUTOMATED_CI_STACK`).
- `validar-matriz.py` sem problemas sobre a matriz entregue/gerada.
- Relatorio ZAP da varredura autenticada avaliado: zero altos abertos.
- Matriz com `problems` vazio (`SecurityBaseline.validate()`).

## 7. Fora desta tarefa (fronteiras)

- Execucao do pentest externo: **LC.8** (escopo em
  `ESCOPO-PENTEST-EXTERNO-LC8.md`).
- OWASP Top 10 para LLM 2025: criterio **da ED-43**, registrado na
  matriz; nenhum teste de LLM e executado aqui.
- Testes de contrato/seguranca da API sao da **P2.26**; a P3.8 cobra a
  evidencia deles na matriz, nao os reescreve.
