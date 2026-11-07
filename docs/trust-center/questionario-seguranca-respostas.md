# Resposta padrão a questionários de segurança — Ziip SAN (Trust Center)

> Respostas institucionais reutilizáveis em questionários de clientes e
> de licitações. **Regra de uso:** cada resposta cita a sua fonte no
> projeto; onde a fonte ainda não existe, a resposta é
> `[PENDENTE — fonte]` e deve ser enviada assim — nunca completada com
> afirmação sem lastro. Atualizar a fonte atualiza a resposta.

## Hospedagem e provedor

- **Onde o serviço é hospedado?** AWS, região `sa-east-1`, de forma
  gerenciada (decisão registrada no ADR-0011, aceito em 12/10/2026;
  implantação no lançamento comercial pelo ADR-0033). Fonte: P0.26.
- **Quais certificações o provedor possui?** Do PROVEDOR (AWS), não do
  Ziip SAN: certificações ISO 9001, ISO 27001 e ISO 27017; relatórios de
  asseguração SOC 1 e SOC 3 (auditor independente). Os relatórios com
  escopo em `sa-east-1` são baixados do AWS Artifact para o kit da
  P0.44. Fonte: P0.26.
- **O Ziip SAN possui certificação ISO/SOC própria?**
  `[PENDENTE — nenhuma certificação própria consta das fontes do projeto; não afirmar.]`

## Arquitetura de segurança

- **Critério de segurança de aplicação:** OWASP ASVS 5.0 — nível 2 em
  geral e nível 3 em autenticação, pagamento e dados pessoais —, com
  matriz requisito/teste/evidência gerada no CI, varredura DAST
  autenticada (OWASP ZAP) e testes dos casos de enumeração, troca de
  token entre realms e acesso assistido/emergência. Fonte: P3.8 (PLT-08).
  Estado das evidências: ver a seção ASVS do Trust Center.
- **Isolamento entre tenants:** separação por tenant com RLS no banco e
  testes de isolamento (inclusive na restauração seletiva da P3.33 e no
  pentest da LC.8). Fontes: P3.8, P3.33, LC.8.
- **Acesso de suporte à conta do cliente:** acesso assistido só com
  consentimento do tenant e acesso de emergência só em incidente de
  segurança declarado, somente leitura, por `PLATFORM_OWNER` com MFA no
  momento e aviso ao tenant em até 24 h — tudo auditado (ADR-0020).
  Fonte: P3.17.

## Vulnerabilidades e testes de invasão

- **Como reportar uma vulnerabilidade?** Pela política pública de
  divulgação responsável deste Trust Center, com `security.txt`
  (RFC 9116), salvaguarda legal ao pesquisador de boa-fé e fluxo
  registrado de recebimento, triagem e resposta. Fonte: P3.38.
- **Há pentest externo?** `[PENDENTE — LC.8]` O pentest externo sobre a
  produção na AWS, com escopo na baseline da P3.8, é executado antes do
  primeiro dado real, sem achado crítico ou alto aberto; o resumo será
  publicado neste Trust Center. Ainda não executado: nenhum resultado
  pode ser afirmado. Fonte: LC.8.

## Dados pessoais (LGPD)

- **Papéis:** tenant controlador, Ziip SAN operador (ADR-0002; DPA nas
  minutas da P3.9). Suboperadores listados neste Trust Center, com
  aviso de mudança; provedor estrangeiro só com cláusulas-padrão da
  ANPD (Res. CD/ANPD 19/2024). Fontes: P3.9, P3.38.
- **Encarregado:** o da plataforma está na seção Encarregado deste Trust
  Center `[PENDENTE — nomeação/registro]`; o de cada tenant é exibido no
  portal do tenant (fonte `lgpd.dpo_contact`, Res. CD/ANPD 18/2024).
  Fonte: P3.40.
- **Incidente com dado pessoal:** o operador informa o controlador sem
  demora injustificada e dentro do prazo contratual
  (`lgpd.operator_notice_hours`); comunicação à ANPD pelo controlador em
  3 dias úteis, complementação em 20 dias úteis (Res. CD/ANPD 15/2024);
  registro guardado por 5 anos. Fonte: P3.10.
- **Retenção e eliminação:** chamados pela vigência + 5 anos;
  eliminação de fim de contrato com certificado (hashes, expiração de
  backups e a exceção dos chamados), precedida de carência e exportação
  completa ao tenant. Fontes: P3.19, P3.16; resumo em
  `privacidade-e-retencao.md`.

## Continuidade e operação

- **Status e incidentes:** página pública de status na mesma hospedagem
  deste Trust Center, com status derivado de sondas (sem edição manual),
  janelas de manutenção publicadas com 72 h de antecedência e resumos de
  revisão pós-incidente publicados. Fonte: P3.18 (P3.11 para o modelo de
  revisão).
- **Cópias e restauração:** cópia lógica diária por tenant em
  armazenamento com retenção imutável (expiração em até 35 dias,
  RNF-43) e restauração seletiva de um tenant sem afetar os demais.
  Fonte: P3.33.
- **Disponibilidade contratual:** `[PENDENTE — P3.9/LC.7]` A meta de
  99,9% do nível Setor Público é medida a partir do lançamento (LC.7) e
  a política de SLA de suporte faz parte das minutas da P3.9; não
  afirmar número como resultado medido antes do lançamento.
