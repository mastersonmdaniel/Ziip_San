# Privacidade e retenção — resumo público da plataforma (Trust Center)

> Resumo institucional do Ziip SAN **operador**. A política de
> privacidade de cada **tenant** (controlador) é versionada e publicada
> no portal do tenant pela P3.40; o encarregado de cada tenant é exibido
> no portal, na política do tenant e nos documentos, a partir da fonte
> única `lgpd.dpo_contact` (Res. CD/ANPD 18/2024). Este resumo não
> substitui nem a política do tenant nem o DPA das minutas da P3.9.

## Papéis (LGPD)

- O tenant é **controlador** dos dados dos seus titulares; o Ziip SAN é
  **operador** (ADR-0002; minutas da P3.9).
- Suboperadores: lista pública neste Trust Center
  (`platform.subprocessor`, core `V10_6`), com aviso de mudança ao
  controlador antes da vigência (P3.9). Provedor fora do país só entra
  na lista pública com as cláusulas-padrão contratuais da ANPD
  incorporadas (Res. CD/ANPD 19/2024) — a publicação sem elas é
  recusada pelo sistema.

## Retenções com fonte no projeto

| Dado | Retenção | Fonte |
|------|----------|-------|
| Chamados de suporte | Vigência do contrato **+ 5 anos** (`purge_at`), com a exceção prevista no certificado de descarte do DPA | ADR-0020; P3.19 (`TicketRetentionService`) |
| Registro de incidente com dado pessoal | **5 anos** (`ret.incident_record_years`) | P3.10 |
| Cópias de segurança (backups) | Retenção imutável com expiração em **até 35 dias** (RNF-43), com crypto-shredding no descarte | P3.33 |
| Relato de vulnerabilidade (P3.38) | **[PENDENTE]** — a tabela de retenção do relato entra na tabela de retenção do módulo quando o DPA da P3.9 for aprovado; nenhum prazo é afirmado antes disso | P3.38/P3.9 |

Retenções fora desta tabela (dados de faturamento, fiscais e demais
módulos) seguem a tabela de retenção de cada módulo e a guarda legal
aplicável (LGPD, art. 16), detalhadas no DPA e no registro de operações
de tratamento do tenant (P3.40) — não são resumidas aqui para não
afirmar número que pertence a outra fonte.

## Direitos do titular

Os pedidos do titular (LGPD, art. 18) são atendidos pelo canal de
privacidade do tenant no portal e no atendimento (P3.40), com protocolo,
verificação de identidade e o prazo de 15 dias da declaração completa
(LGPD, art. 19, II). O Trust Center é institucional e não recebe pedido
de titular.

## Incidente com dado pessoal

Procedimento da P3.10: o operador informa o controlador sem demora
injustificada e dentro do prazo contratual
(`lgpd.operator_notice_hours`); o controlador comunica à ANPD em 3 dias
úteis, com complementação em 20 dias úteis (Res. CD/ANPD 15/2024),
usando os modelos entregues pela P3.10.
