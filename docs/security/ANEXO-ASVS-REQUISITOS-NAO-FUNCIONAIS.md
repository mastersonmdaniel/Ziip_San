# Anexo ASVS — para `docs/arquitetura/REQUISITOS-NAO-FUNCIONAIS.md` (P3.8)

> Como integrar: anexar este conteudo ao REQUISITOS-NAO-FUNCIONAIS na
> integracao da camada. Do arquivo original, o espelho via MCP leu o
> trecho introdutorio (sucesso parcial, HEAD `0fbcd22` confirmado ao
> vivo — ver `espelho/LEIA-ME.md` da frente P3.8); as definicoes linha
> a linha das RNF-30 a RNF-39 nao vieram no trecho retornado, por isso
> o anexo e entregue como arquivo proprio e as ligacoes RNF nao
> confirmadas estao marcadas — conferir contra o texto integral antes
> de fechar a camada. (Correcao D9 da revisao cruzada: a primeira
> redacao deste anexo contava a historia errada, de um "HTTP 429" que
> o proprio pacote ja havia corrigido no relatorio e no LEIA-ME.)

## Criterio

OWASP ASVS 5.0 e o criterio de aceite de seguranca do produto: nivel 2
em geral, nivel 3 em autenticacao, pagamento e dados pessoais (cartao
do dono de 29/10/2026). A prova vive na matriz
requisito-teste-evidencia (`docs/security/matriz-requisito-teste-evidencia.csv`,
gerada/conferida no CI pela P3.8) e no relatorio do pentest externo da
LC.8, que usa esta mesma linha de base (PLT-08).

## Capitulos ASVS 5.0 x RNF-30 a RNF-39 x casos

| Cap. ASVS 5.0 | RNF | Fonte da ligacao | Casos P3.8/LC.8 |
|---|---|---|---|
| 1 Architecture | RNF-30..RNF-39 (a confirmar) | sem citacao especifica nas fontes locais | DAST, isolamento |
| 2 Authentication | RNF-30..RNF-39 (a confirmar); RNF-36 via LC.8 | LC.8 cita RNF-36 para o pentest | enumeracao login/recuperacao, realms, defesa automacao, emergencia |
| 3 Session Management | RNF-30..RNF-39 (a confirmar) | sem citacao especifica | troca de realms; apps (LC.8) |
| 4 Access Control | RNF-38 | levantamento 18h: testes de RLS/isolamento como evidencia da RNF-38 | vinculo, convidado, download, assistido, emergencia, isolamento |
| 5 Validation, Sanitization and Encoding | RNF-30..RNF-39 (a confirmar) | sem citacao especifica | DAST |
| 6 Stored Cryptography | RNF-32 | LC.4/ADR-0011: RNF-32 para KMS/KEK/cofre | cofre, isolamento |
| 7 Error Handling and Logging | RNF-30..RNF-39 (a confirmar) | sem citacao especifica | assistido, emergencia, cofre, A10 |
| 8 Data Protection | RNF-38 | levantamento 18h (isolamento) | download, isolamento |
| 9 Communication | RNF-33 | levantamento 18h: comunicacao segura SSL sob RNF-33 | (transversal; DAST/LC.8) |
| 10 Malicious Code | RNF-35 | P0.40: SBOM/licencas sob RNF-35 | cadeia de suprimentos (A03) |
| 11 Business Logic | RNF-30..RNF-39 (a confirmar) | sem citacao especifica | matricula, convidado, A10, LLM (ED-43) |
| 12 File and Resources | RNF-30..RNF-39 (a confirmar) | sem citacao especifica | (download cobre o capitulo via caso DOWNLOAD_PORTAL no registro Java — ver matriz) |
| 13 API and Web Service | RNF-36 | LC.8 cita RNF-36 para o pentest | API publica (P2.26), DAST |
| 14 Configuration | RNF-35 | P0.40/SBOM sob RNF-35 | cofre, DAST, A03 |

## Registros complementares na matriz

- OWASP Top 10:2025 completo, com A03 (falha na cadeia de suprimentos
  de software) e A10 (tratamento de condicoes excepcionais) cobertos
  por casos proprios. Taxonomia OFICIAL de 2025 (correcao D1 da
  revisao cruzada — a primeira versao trazia a lista de 2021): A02
  Security Misconfiguration, A04 Cryptographic Failures, A05
  Injection, A06 Insecure Design, A07 Authentication Failures, A09
  Security Logging and Alerting Failures; a baseline da LC.8/ED-73
  herda esta mesma lista via `SecurityBaseline.owaspTop10_2025()`.
- OWASP API Security Top 10 2023 aplicado ao teste da API publica da
  P2.26 (API1 a API10 na matriz).
- OWASP Top 10 para LLM 2025 registrado como criterio da ED-43.
- RNF-39 (tokens moveis em Keychain/Keystore, ED-18) entra no escopo da
  LC.8 pelos apps; nao tem caso local na P3.8.
