## 1. Estado atual (28/10/2026)

- **Fase.** Plano e documentação. O P0 não começou e aguarda a ordem do dono do produto. Desde 12/10/2026 o
  repositório só recebeu documentação: plano v2.12 a v2.21, ADRs 0035 a 0044, biblioteca de normas, levantamentos 20a a
  22d e relatórios 23a a 23e. Nenhuma linha de código mudou desde as fundações.
- **Fundações.** E0 a E6 concluídas em 12/10/2026, com 973 testes Java e 134 do frontend verdes na última execução
  registrada no diário. O relatório 23c (28/10/2026) fez leitura estática, sem build nem execução de teste.
- **Código existente, segundo o 23c.** Treze módulos Maven com fonte (≈ 25,6 mil linhas Java de código e ≈ 15,3 mil de
  teste) e catorze diretórios de módulo vazios. Só o cadastro (`registry`) está exposto pela API, de ponta a ponta, com
  login OIDC e PKCE; faturamento, medição e arrecadação são motores puros com teste, sem serviço, API nem lote; MTR,
  LGPD, qualidade, perdas e GIS têm serviço fora do scan da aplicação; outbox inexistente. As regras de consumo estão
  escritas duas vezes (billing e metering).
- **Isolamento.** As 76 tabelas de tenant têm RLS forçada e nenhuma role tem BYPASSRLS; CPF e CNPJ cifrados com índice
  cego no cadastro e no MTR; auditoria sem dado pessoal. Os riscos estão na plataforma: a role da API grava o catálogo
  de tenants, o provisionamento aceita schema existente e a GUC é de sessão, incompatível com PgBouncer em modo
  transaction (P0.17, P0.28).
- **Divergências já tratadas pelo plano.** Token em `sessionStorage` e CSRF desligado (BFF, P0.14), rotas sem
  `/api/v1` (P0.10), URN e códigos de problema em português (P0.33), PostgreSQL 15 (P0.4), migração no processo da API
  (P0.6), compose com serviços em `0.0.0.0` e credenciais padrão (P0.7), `KeycloakAdminService`, tabelas legadas na
  `V1_1`, SINIR e SCADA por MQTT contra os ADRs (limpeza da P0.25, antes da renomeação).
- **Ambiente.** Só local: sem Dockerfile, CI remoto, backup nem observabilidade; chave de cifra só de desenvolvimento;
  nenhum dado pessoal real. Código ainda editado no Google Drive (a P0.1 o tira de lá). Repositório privado no GitHub
  (`mastersonmdaniel/Ziip_San`), com push ao fim de cada etapa.
- **Hospedagem.** AWS `sa-east-1` gerenciada (ADR-0011), implantada só no lançamento comercial; até lá, desenvolvimento,
  homologação e demonstração locais, sem dado pessoal real (ADR-0033).
- **Piloto.** Não definido (§2.4).
- **Editais.** Dos 2.663 requisitos distintos da VISAN e do SAMAE (consolidado 18), 3% atendidos, 21% em parte e 75% não
  atendidos; o produto hoje reprova nas duas provas de conceito. A pesquisa de editais é suplemento de planejamento,
  não atendimento de edital (diretriz de 21/10/2026).
- **Base documental.** 46 ADRs (0001 a 0046, com o 0029 revogado e o 0046 proposto); catálogo de 1.676 normas
  (1.330 conferidas em fonte oficial, 346 em fonte secundária); 333 achados com destino único no mapa de donos.

**Histórico.** A v1 consolidou o PDCA de 12/10 (C1–C5) e o roadmap R0–R9 do `15c` em fundações E0–E6, com mapa Flyway e
portões de decisão; as etapas foram executadas no mesmo dia, um commit por etapa (diário §18). A revisão 17, sobre
`fb2b53d`, concluiu que o código está correto mas ainda não é serviço operável, o que motivou a v2. A v3.0 consolida as
versões 2.1 a 2.21 e os relatórios 23a a 23e, sem mudança de código.

---
