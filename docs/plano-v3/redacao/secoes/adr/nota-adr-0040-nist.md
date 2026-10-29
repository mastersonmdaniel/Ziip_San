# Nota para o ADR-0040: alinhamento ao NIST SP 800-63B-4

Texto a acrescentar ao ADR-0040, logo abaixo da linha **Afeta**, como linha de esclarecimento (regra 2 do índice de
ADRs: sem mudança de substância). Origem: 22c, PLT-30 (tarefa dona ED-06; passkey no portal pela ED-38); 23a, COE-07.

---

- **Nota de alinhamento ao NIST SP 800-63B-4 (28/10/2026, plano v3.0), esclarecimento sem mudança de decisão:** a
  revisão 4 final do NIST SP 800-63B (31/07/2025) exige comparar a senha nova com lista de senhas vazadas ou proibidas,
  veda exigir troca periódica sem evidência de comprometimento, veda regras de composição e recomenda mínimo de 15
  caracteres quando a senha é o único fator e de 8 quando há MFA; admite passkey sincronizável em AAL2 (22c, F07). Os
  padrões de fábrica deste ADR já seguem essa linha: lista de senhas vazadas obrigatória, mínimo de 12 com MFA
  disponível, `sec.password_max_age_days` desligado e histórico de 5. A leitura dos itens 4 e 5 passa a ser esta:
  (1) expiração periódica e regra de complexidade continuam disponíveis por tenant para atender a exigência de edital
  (por exemplo, POC-05), mas o console as marca como contrárias à recomendação do NIST e só as liga com aceite
  registrado do tenant (classe b); (2) quando o tenant dispensa o MFA para um perfil (ADR-0007), o console avisa que o
  mínimo recomendado para senha como único fator é 15 caracteres, sem mudar o padrão de fábrica; (3) o autenticador
  próprio da ED-06 aplica a lista de senhas vazadas também na troca e na redefinição de senha, e o teste com dois
  tenants do item 5 cobre essas regras; (4) passkey (WebAuthn, já previsto no ADR-0007 para o backoffice, com suporte no
  Keycloak desde a 26.4.0) entra no portal do consumidor pela ED-38, com o parâmetro `portal.passkey_enabled` (classe d),
  no realm `ziipsan-portal`, que este ADR não rege (ADR-0022, ADR-0043).

---

**Parâmetros citados.** `sec.password_min_length`, `sec.password_max_age_days`, `sec.password_history`,
`sec.lockout_attempts` (PARAMETROS-DO-TENANT, ADR-0040) e `portal.passkey_enabled` (novo, classe d, proposto pelo 22c,
PLT-30, a acrescentar ao catálogo do tenant com a ED-38).
