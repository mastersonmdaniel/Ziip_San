# Anexo II — DPA / LGPD (ESTRUTURA — texto jurídico é da P3.9)

> Fontes: aceite da **P3.9** (minutas: contrato SaaS, DPA, termos, SLA,
> suboperadores), **P3.10** (resposta a incidente; modelo de RIPD), ADR-0002,
> ADR-0019, ADR-0020, ADR-0033, RAS-20. Este anexo organiza **o que a minuta
> da P3.9 precisa conter**, item a item do aceite dela, e registra os
> parâmetros que o código já entregue (P3.10) espera receber do contrato.
> **Nenhuma cláusula foi redigida aqui.**

## 1. Papéis (fixados pela P3.9)

- **Ziip SAN: operador.**
- **Cliente (tenant): controlador.**
Redação das obrigações de cada papel: **[A DEFINIR NA P3.9]**.

## 2. Comunicação de incidente operador → controlador (P3.9 + P3.10)

- A Res. CD/ANPD **15/2024 não fixa número** para o aviso do operador; a
  ANPD recomenda fixá-lo em contrato (aceite da P3.9). Logo, o contrato
  **fixa o teto** do parâmetro `lgpd.operator_notice_hours` (classe c, **só
  reduz**: o tenant pode encurtar, nunca alargar).
- Teto contratual de `lgpd.operator_notice_hours`: **[A DEFINIR NA P3.9]**
  (a entrega da P3.10 recusa validar o parâmetro sem esse teto registrado —
  ver `LgpdIncidentParameters`).
- O operador informa o controlador **sem demora injustificada** e dentro
  desse prazo contratual; o relógio do incidente começa no **conhecimento
  pelo Ziip SAN**, com alertas na Central às **24 h** e às **48 h** (P3.10).
- Prazos **do controlador** perante ANPD/titulares (Res. CD/ANPD 15/2024,
  via P3.10): comunicação em **3 dias úteis**; complementação em **20 dias
  úteis**. Os modelos de formulário à ANPD e de aviso aos titulares são
  **entregues ao controlador** pelo operador (P3.10).
- Registro do incidente guardado por **5 anos** (`ret.incident_record_years`,
  P3.10).

## 3. Retenção de chamados e certificado de descarte (P3.9)

- Chamados retidos pela **vigência do contrato + 5 anos** (ADR-0020).
- O DPA prevê a **exceção** dessa retenção no **certificado de descarte**
  (aceite da P3.9) — o certificado da saída (Anexo III, P3.16) carrega essa
  exceção. Redação: **[A DEFINIR NA P3.9]**.

## 4. Suboperadores (P3.9)

Estrutura exigida pelo aceite da P3.9:

- **Lista de suboperadores** como anexo vivo do DPA, com **aviso de mudança
  ao controlador**. Prazo e forma do aviso: **[A DEFINIR NA P3.9]**.
- Para **todo provedor fora do país** (o aceite cita: modelo de linguagem,
  Meta, Telegram e demais da lista): **cláusulas-padrão contratuais da ANPD
  incorporadas ao contrato** (Res. CD/ANPD **19/2024**; LGPD arts. 33 a 36)
  e transferência registrada no **RIPD** pelo modelo da P3.10.
- **Trava de aprovação** (teste de conferência da P3.9): suboperador
  estrangeiro **sem** cláusula-padrão **bloqueia** a aprovação da minuta.
- A lista nominal de suboperadores **não consta das fontes** desta entrega:
  **[A DEFINIR NA P3.9]** (nenhum provedor foi listado aqui além dos que o
  próprio aceite da P3.9 exemplifica).

## 5. Homologação local e assinatura (P3.9 / LC.9)

- **Termo da homologação local assinado, sem transferência de dado
  pessoal** (anonimização na origem) — ADR-0033.
- Contrato e DPA **assinados só na LC.9**, antes do primeiro dado real, a
  partir das minutas aprovadas na P3.9.
- Telemetria de produto **agregada** registrada no DPA (P4.8/LC.9): sem
  identificador de pessoa, sem gravação de sessão, sem conteúdo digitado.
  Redação: **[A DEFINIR NA P3.9]**.

## 6. Cláusulas de aviso, restrição e saída (P3.9)

O aceite da P3.9 exige cláusulas de **aviso**, de **restrição só manual** e
de **saída**. No plano, a restrição manual já está decidida: suspensão
sempre manual, com aviso prévio, por pessoa com MFA, com justificativa e
auditoria (ADR-0018/0019 — ver contrato-modelo §2.7). A **redação** das três
cláusulas é: **[A DEFINIR NA P3.9]**.
