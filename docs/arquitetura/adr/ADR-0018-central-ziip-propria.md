# ADR-0018 — Central Ziip própria no Ziip SAN, com o console da plataforma

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17 (achado P1); ADR-0007; ADR-0014; ADR-0020 (acesso assistido); P1.6; `docs/arquitetura/CENTRAL-ZIIP.md`
- **Ajuste de 12/10/2026:** itens 2 e 4 alinhados às decisões do dono do produto sobre suspensão e eliminação e ao
  ADR-0020 (acesso assistido no lugar da impersonação), antes de qualquer implementação.
- **Segundo ajuste de 12/10/2026 (plano v2.2), antes de qualquer implementação:** item 2 passa a dizer quais papéis
  executam suspensão, bloqueio e eliminação e fixa a carência de 30 dias; item 4 cita o acesso de emergência do
  ADR-0020; decisões do dono do produto.

## Contexto

O produto não tem console da plataforma nem visão operacional dos tenants (17:P1). O Ziip Cloud tem a Ziip Central,
painel do papel `gestor` com 2FA, coletor de 5 min, alertas e kit de evidências. Ela foi feita para venda B2C de NFS-e:
lê tabelas de tenant sem filtro, mostra e-mail e IP de clientes e confere MFA pelo cadastro, não pela sessão. O
saneamento precisa de outros indicadores (ciclo, arrecadação, Pix, fiscal, prazos regulatórios) e o operador não pode
ler dado pessoal do controlador.

## Decisão

1. **Central própria, adaptada, dentro do monólito modular**, como módulo de plataforma executado nos processos `api` e
   `worker`. Conceitos do Ziip Cloud são reescritos em Java; o código C# não é portado.
2. **Acesso só por papéis de plataforma** (dono, operação, suporte, financeiro), com MFA sempre exigido e verificado no
   token (ADR-0007). Ações destrutivas pedem step-up (reautenticação com segundo fator), justificativa e auditoria.
   Suspensão, bloqueio e eliminação de dados de tenant são executados por **uma pessoa** com MFA, sem segundo
   aprovador. Suspensão e bloqueio: dono (`PLATFORM_OWNER`) ou financeiro (`PLATFORM_FINANCE`), este só por
   inadimplência contratual. Eliminação: só o dono, depois de carência de 30 dias contados do fim do contrato, com
   certificado de descarte em até 30 dias após a carência (ADR-0015, RNF-42).
3. **Indicadores de saneamento** por tenant e consolidados, conforme `CENTRAL-ZIIP.md` §3, com Health Score adaptado.
4. **Incorpora o console da plataforma**: implantação com checklist de parâmetros, planos, módulos, cotas, flags,
   suspensão manual com aviso prévio (backoffice em somente leitura; portal do consumidor, Pix, retornos bancários e
   2ª via continuam), saída com certificado de descarte e acesso assistido com consentimento, prazo e auditoria
   (ADR-0020); sem consentimento, só o acesso de emergência em incidente de segurança declarado, somente leitura, pelo
   dono (ADR-0020, seção Exceção).
5. **Nunca lê dado pessoal de tenant.** Role `ziipsan_central` (nome posterior à renomeação do ADR-0016) acessa só
   funções agregadoras; as tabelas do schema `platform` guardam `tenant_id`, contagens e durações.
6. **API de métricas `v1` estável** desde a primeira versão, para permitir uma Central única dos produtos Ziip. A
   Central única não é escopo deste ADR.

## Consequências

### Positivas

- Um só lugar para operar e acompanhar todos os tenants, com prova de cada ação.
- O isolamento fica verificável por teste: a role da Central não lê tabela de tenant.
- A API estável evita retrabalho quando houver a Central única.

### Negativas

- Mais um módulo a manter por uma pessoa; a Central v0 cresce junto com as fases.
- Funções agregadoras por shard precisam de revisão a cada indicador novo para não vazar coluna pessoal.
- Duas Centrais (Ziip Cloud e Ziip SAN) convivem até a Central única.

## Alternativas consideradas

- **Usar a Ziip Central do Ziip Cloud.** Rejeitada: outro banco e outra stack, leitura cruzada de dado pessoal e
  indicadores B2C.
- **Serviço separado.** Rejeitada agora: mais um componente em produção (ADR-0010); a API estável mantém essa porta.
- **Só painéis num backend de métricas (Grafana ou similar).** Rejeitada como solução única: não executa ações
  auditadas; admitida para métricas técnicas.
- **Central única de todos os produtos já.** Rejeitada: fora do escopo e do prazo do go-live.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Papéis de plataforma no realm, verificação de `amr`, 403 `mfa_obrigatorio` | P0 (com P0.13 e P0.15) |
| Schema `platform`, role `ziipsan_central`, funções `platform_stats.*` com teste de ausência de dado pessoal | P1 |
| Console v0 (P1.6): implantação, planos, cotas, flags, suspensão, saída | P1 |
| Central v0: coletor com trava, alertas, Visão geral, Plataforma, Segurança; API de métricas `v1` | P1 |
| Abas Faturamento e Fiscal | P2 |
| Abas Arrecadação, Pix, LGPD, Regulatório, Infraestrutura com SLO, Radar, Suporte (ADR-0020); kit de evidências | P3 |
| Acesso assistido com consentimento e acesso de emergência (ADR-0020), no lugar da impersonação | P3 |
| Ensaio de saída com tenant sintético, carência de 30 dias e certificado de descarte | P3 |
