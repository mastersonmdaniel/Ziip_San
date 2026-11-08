# Portal ampliado — ED-38 (contrato de integração)

## Realm `ziipsan-portal` — IdP gov.br (ADR-0043)
- Broker OIDC no Keycloak: authorization code + PKCE S256, client confidencial `ziipsan-portal-bff` (P3.3).
- Só tenant de direito público (`reg.legal_nature` ∈ AUTARCHY/PUBLIC_COMPANY/SEM — literais da P3.3), com `portal.idp_govbr_enabled=true`, checklist de elegibilidade completo (GovBrOnboarding) e `portal.idp_govbr_min_level` ∈ {SILVER, GOLD}.
- Exigência por operação: `portal.min_assurance_by_operation` da P3.3 (prata/ouro: troca de titularidade, tarifa social, negociação de dívida, alteração de contato) via `GovBrPolicy.requireAssurance` — não reimplementada.
- Login local CPF/CNPJ/e-mail (P3.3): disponibilidade derivada dos canais configurados no realm — disponível somente quando o conjunto de canais contém `LOCAL` (`localLoginAvailable(Set<EntryChannel>)`, correção D4); com apenas `GOVBR` configurado, o login local fica indisponível.
- Passkey: WebAuthn com UV obrigatória, gated por `portal.passkey_enabled` (ED-06).

## Rotas do autoatendimento (BFF)
- GET  /portal/faturas?unidade= — pendentes+quitadas 12 meses (POC-69)
- GET  /portal/faturas/{id}/segunda-via — pendente ou quitada (P3.3/P3.20)
- POST /portal/simulacao/fatura | /portal/simulacao/parcelamento — simulação (não cria acordo; acordo é P4.1). A simulação de parcelamento usa juros simples por desenho (correção D3); a escolha do `interest_method` da P4.1 (PRICE/COMPOUND/NONE) para o acordo real fica **[A DEFINIR]** na integração, conforme a política do tenant.
- GET  /portal/faturas/{id}/explicacao — explicação determinística do cálculo gravado (também função da ED-43)
- GET  /portal/certidao-quitacao-anual?ano= — documento autêntico (ED-34/P3.34)
- GET  /portal/validar?tipo=&numero=&codigo= — validação pública do código verificador/QR
- POST /portal/relatos — sem login, sob defesa P3.3; protocolo P1.36 LOCATION_ONLY; foto OBRIGATÓRIA e contato opcional (correção D6)
- GET  /portal/abastecimento?endereco= — ED-35 PublicOutageStatus (sem reimplementação)
- GET  /portal/agendamento/janelas | POST /portal/agendamento — janelas ED-36
- GET  /portal/centralizador/... — visão ED-22 (CentralizerViewService, sem reimplementação)

## Acessibilidade (P0.36)
WCAG 2.2 AA: axe sem serious/critical; declaração do tenant publicada (P3.3); VLibras só após consulta formal (P3.3). SAMAE 2.20: ensaio ≥90% (PortalCoverage).

## Fora desta entrega
- Aviso "a caminho" e aceite na OS: ED-20. Assistente conversacional: ED-43 (só a função de explicação é exposta). Ocorrência de rede do relato: ED-92 (inexistente). Lembrete de agendamento: ED-41.
