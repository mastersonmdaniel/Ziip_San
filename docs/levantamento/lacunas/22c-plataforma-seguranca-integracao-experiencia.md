# 22c: plataforma, segurança, integração e experiência. Funções esquecidas, detalhamentos rasos e inovações

Frente PLATAFORMA, SEGURANÇA, INTEGRAÇÃO E EXPERIÊNCIA. Documento de análise de lacunas, só leitura sobre o plano v2.17, os ADRs 0001 a 0041 e os levantamentos. Nada aqui altera decisão aceita; cada achado é recomendação a ser levada ao dono do produto por cartão (ADR-0038). Data da análise: 22/10/2026. Identificadores técnicos em inglês; textos ao usuário em português.

## 0. Leitura rápida

1. Foram registrados 42 achados: 25 ausentes do plano (A), 12 presentes mas rasos (B) e 5 inovações (C). Nenhum contradiz ADR aceito.
2. A base de plataforma é sólida (isolamento por RLS, BFF, MFA por tenant, cofre por tenant, outbox, Central Ziip, acesso assistido). Esses itens não são repetidos (seção 4).
3. Maior vazio de plataforma: não existe produto de integração para terceiros. A API é só a do frontend; falta credencial por tenant, escopos, cota, ambiente de teste, ciclo de vida da API e webhook de saída (PLT-01, PLT-02). A VISAN exige API REST compatível com a e-PING como item obrigatório (TR-027, TR-028), e o 18h M81 o deixa "não planejado".
4. Maior vazio de continuidade: nenhuma restauração seletiva de um tenant (o PITR do RDS volta o cluster inteiro, com cerca de 50 tenants) e nenhuma decisão sobre desastre regional. A AWS tem uma única região no Brasil, e o TR-061 da VISAN veda guardar ou espelhar dados fora do país (PLT-03, PLT-04).
5. Detalhe a corrigir no desenho: a especificação Pix do Banco Central protege o webhook por mTLS e não define assinatura de payload; o plano fala em "assinatura do PSP" (ARQUITETURA-ALVO §2 item 4, P3.2) (PLT-26).
6. Calendário regulatório que muda tarefa: convênios de serviço público saem do débito automático interbancário para o Pix Automático em 01/01/2027 (Res. CMN 5.278/2025). A P3.24 (FEBRABAN 150 legado) tende a perder o objeto antes do lançamento (PLT-27).
7. Golpe de cobrança falsa por boleto, Pix e WhatsApp está em alerta ativo de concessionárias e da Febraban (outubro de 2026) e o plano não prevê verificação pública de autenticidade da fatura (PLT-11).
8. Segurança: o plano cita OWASP só pelo ZAP. Falta adotar o ASVS 5.0 como critério de aceite, política de divulgação responsável e Trust Center para habilitação (PLT-08, PLT-09).
9. Escala de implantação: o importador é desenhado para o piloto. Para centenas de tenants com legados diferentes, falta o framework declarativo de migração (PLT-16) e a jornada de implantação visível ao cliente (PLT-21).
10. Vinte e nove dos 42 achados viram parâmetro do tenant pelo ADR-0038 (os outros 13 são decisões da plataforma); as chaves propostas estão no detalhamento e dependem de cartão.
11. Dez achados de maior valor: PLT-01, PLT-03, PLT-04, PLT-08, PLT-10, PLT-11, PLT-16, PLT-18, PLT-26 e PLT-27.

## 1. Método e fontes

### 1.1 Método

1. Leitura integral de `REQUISITOS-NAO-FUNCIONAIS.md`, `ARQUITETURA-ALVO.md`, `CENTRAL-ZIIP.md`, `AJUDA-SUPORTE-SOBRE.md`, `MARCA-E-TEMAS.md`, `PLATAFORMA-E-VERTICAIS.md` e do levantamento 17; leitura dos ADRs 0005, 0006, 0007, 0019, 0026, 0040 e consulta aos demais; leitura das fases P0 a P4, da trilha de editais e da fase LC do plano (linhas 715 a 1115); consulta ao `PARAMETROS-DO-TENANT.md` por chave e aos levantamentos 10, 12, 13, 18a, 18b, 18c, 18f, 18h, 20e, 21a, 21b.
2. Antes de classificar algo como ausente, busca por sinônimos em português e inglês em todo o plano, ADRs e levantamentos (por exemplo: `webhook`, `API pública`, `OAuth`, `domínio próprio`, `CNAME`, `OCR`, `SIEM`, `security.txt`, `bug bounty`, `fuso`, `timezone`, `busca global`, `atalho`, `delegação`, `substituto`, `game day`, `assinatura eletrônica`, `PAdES`, `golpe`, `phishing`). Só o que não apareceu foi classificado como A.
3. Pesquisa externa ampla (seção 1.3), com prioridade a fontes oficiais. Oito fatos decisivos foram reabertos por mim em fonte primária (marcados [V]): FAQ do Login Único, especificação da API Pix, nota do Banco Central sobre a cobrança híbrida, texto da Res. CMN 4.790 com a Res. CMN 5.278, FAQ da ANA sobre tarifa social, lista de regiões da AWS, diretriz 4.2.6 da Apple e notas de versão do Keycloak sobre SCIM. Os demais vêm de levantamento por agentes de apoio ([A]) ou de fonte secundária ([S]). O que não foi confirmado leva [INFERÊNCIA].
4. Verificação cruzada com os documentos `22b-operacao-campo-rede-inteligencia.md` (OPE) e `22d-gestao-regulatorio-inovacao-de-mercado.md` (GES) da mesma série, para não duplicar: onde há sobreposição, o achado informa a referência e trata só do ângulo de plataforma.

### 1.2 Convenções

| Item | Significado |
|---|---|
| Classe A | Ausente do plano: nenhuma tarefa, ADR ou requisito trata do assunto |
| Classe B | Presente, mas raso: há tarefa ou requisito, com critério de aceite incompleto ou desatualizado |
| Classe C | Inovação sobre o que o produto já prevê |
| Prioridade | Alta (afeta edital obrigatório, segurança, continuidade ou conformidade), média, baixa |
| Esforço | P, M, G, na escala do plano (P menor que M menor que G); sem conversão em equipe nem prazo |
| Parâmetro do tenant | Sim quando a opção for bifurcação normativa ou preferência do cliente, na classe a, b, c ou d do ADR-0038; "Não" quando for decisão da plataforma |
| [V] | Fonte primária aberta e lida por mim nesta análise |
| [A] | Fonte lida por agente de pesquisa de apoio e não reaberta por mim |
| [S] | Fonte secundária (imprensa, fornecedor, blog) |
| [INFERÊNCIA] | Dedução minha, não confirmada em fonte |

### 1.3 Fontes externas (URL)

Identidade, assinatura e acessibilidade

- F01 [V] Login Único gov.br, dúvidas frequentes (atualizada em 19/06/2026): integração exclusiva de órgãos públicos; sistema em domínio GOV.BR; município na Rede Nacional de Governo Digital. https://www.gov.br/governodigital/pt-br/identidade/identidade-digital-para-gestores-publicos/duvidas-frequentes-do-ecossistema-da-identidade-digital-gov-br
- F02 [A] Roteiro técnico do Login Único (OIDC, PKCE S256, `reliability_info`): https://acesso.gov.br/roteiro-tecnico/iniciarintegracao.html
- F03 [A] Assinatura eletrônica gov.br e manual de integração (credencial só para gestor público, produção em domínio oficial): https://www.gov.br/governodigital/pt-br/identidade/assinatura-eletronica/saiba-mais-sobre-a-assinatura-eletronica e https://manual-integracao-assinatura-eletronica.servicos.gov.br/pt-br/latest/iniciarintegracao.html
- F04 [A] ITI: validador https://validar.iti.gov.br/sobre.html ; autoridades de carimbo do tempo https://www.gov.br/iti/pt-br/assuntos/icp-brasil/autoridades-de-carimbo-do-tempo ; contratação no SERPRO https://www.gov.br/pt-br/servicos/contratar-emissao-de-carimbo-do-tempo ; DOC-ICP-15.03 https://www.gov.br/iti/pt-br/central-de-conteudo/doc-icp-15-03-versao-7-3-pdf (versão 7.3 do documento; a vigente em 22/10/2026 é a 9.1, de 2025)
- F05 [A, espelho] Lei 14.063/2020, arts. 4º e 5º: https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2020/lei/l14063.htm (o Planalto não abriu para o agente; leitura em espelho da ANTT)
- F06 Keycloak: dimensionamento https://www.keycloak.org/high-availability/single-cluster/concepts-memory-and-cpu-sizing [A]; benchmark https://www.keycloak.org/2025/10/keycloak-benchmark [A]; notas de versão (SCIM suportado na 26.8.0, Organizations, passkeys) https://www.keycloak.org/docs/latest/release_notes/index.html [V quanto ao SCIM]
- F07 [A] NIST SP 800-63B-4 (31/07/2025): https://csrc.nist.gov/pubs/sp/800/63/b/4/final e https://pages.nist.gov/800-63-4/sp800-63b/authenticators/
- F08 [A] W3C WCAG 2.2: https://www.w3.org/WAI/standards-guidelines/wcag/ ; critério 3.3.8 https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html ; critério 2.5.8 https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html ; ISO/IEC 40500:2025 https://www.w3.org/press-releases/2025/wcag22-iso-pas/ ; eMAG 3.1 https://www.gov.br/governodigital/pt-br/acessibilidade-e-usuario/acessibilidade-digital/modelo-de-acessibilidade ; TCU, Acórdão 2099/2025 https://sites.tcu.gov.br/recursos/acessibilidade-jurisprudencias/tcu_jurisprudencia/acordao_de_interesse/acessibilidade_digital_no_setor_publico_federal.pdf ; VLibras https://vlibras.gov.br/doc/widget/installation/webpageintegration.html e https://www.vlibras.gov.br/privacy.html
- F09 [A] e-PING e Conecta: https://www.gov.br/governodigital/pt-br/infraestrutura-nacional-de-dados/interoperabilidade/padroes-de-interoperabilidade ; https://www.gov.br/governodigital/pt-br/infraestrutura-nacional-de-dados/interoperabilidade/conecta-gov.br ; https://www.gov.br/conecta/catalogo/apis/cadunico-servicos

Pagamentos, bancos e mensageria

- F10 [V] API Pix do Banco Central, `openapi.yaml` 2.10.0 (webhook com `security: []` e a frase de proteção por mTLS; nenhuma assinatura de payload): https://github.com/bacen/pix-api . [A] Manual de Padrões para Iniciação do Pix: https://www.bcb.gov.br/content/estabilidadefinanceira/pix/Regulamento_Pix/II_ManualdePadroesparaIniciacaodoPix.pdf . [A] FAQ do Pix Automático: https://www.bcb.gov.br/content/estabilidadefinanceira/pix/pix-automatico-FAQ-participantes.pdf
- F11 [V] Nota do BCB de 18/09/2026 (cobrança híbrida a partir de 01/02/2027; Pix Automático em conta-salário a partir de 01/07/2027): https://www.bcb.gov.br/api/servico/sitebcb/notasImprensa/21262
- F12 [V, trecho do texto normativo] Resolução CMN 4.790/2020 consolidada (arts. 2º-A, §§ 1º e 2º, pela Res. CMN 5.251/2025 e Res. CMN 5.278/2025): https://normativos.bcb.gov.br/Lists/Normativos/Attachments/50957/Res_4790_v6_P.pdf ; voto do CMN 92/2025 (Voto 92/2025-CMN, vinculado à Res. CMN 5.278/2025) https://normativos.bcb.gov.br/Votos/CMN/202592/Voto_do_CMN_92_2025.pdf
- F13 [A/S] WhatsApp Business Platform: preços https://developers.facebook.com/docs/whatsapp/pricing/updates-to-pricing/ ; política https://business.whatsapp.com/policy ; CADE https://www.gov.br/cade/pt-br/assuntos/noticias/cade-mantem-multa-diaria-contra-meta-e-whatsapp-por-descumprimento-de-medida-preventiva . Os domínios da Meta recusaram conexão ao agente; as mudanças de 01/10/2026 vêm de integradores [S] (https://help.twilio.com/articles/53100462475931).
- F14 [A] Amazon SES, gerenciamento de tenants e reputação isolada: https://docs.aws.amazon.com/ses/latest/dg/tenants.html ; https://aws.amazon.com/about-aws/whats-new/2025/08/amazon-ses-tenant-isolation-automated-reputation-policies/ ; painel por tenant https://aws.amazon.com/about-aws/whats-new/2026/09/amazon-ses-vdm-tenants/ ; disponibilidade em `sa-east-1` https://docs.aws.amazon.com/general/latest/gr/ses.html
- F15 [S] Requisitos de remetentes em massa de Gmail, Yahoo e Microsoft (fontes secundárias; páginas primárias não abertas): https://www.blkdg.com/blog/email-deliverability-2026/ e https://unspam.email/articles/email-deliverability-trends
- F16 [A] Protesto: cartilha do CNJ https://www.cnj.jus.br/wp-content/uploads/2025/08/cartilha-para-realizacao-de-protesto-06-08-25.pdf ; documentação de apresentante da CENPROT https://cenprot.gitbook.io/documentacao-apresentante (páginas de layout não abertas)
- F17 [V] ANA, tarifa social, perguntas frequentes (cessão direta só a prestador de direito público; ERI intermedeia): https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/perguntas-frequentes . [A] Resolução ANA 271/2025: https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2025/271
- F18 [A] SIRC, Decreto 9.929/2019, art. 7º: https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2019/decreto/d9929.htm ; Serpro, Consulta CPF e Datavalid: https://www.gov.br/pt-br/servicos/obter-solucao-de-consulta-de-dados-de-cadastro-de-pessoa-fisica-cpf
- F19 [S] Alertas de golpe de cobrança falsa (outubro de 2026): Saneago https://goias.gov.br/saneago-alerta-para-golpes-pelo-whatsapp-e-boletos-falsos/ ; BRK https://abcdoabc.com.br/brk-campanha-seguranca-digital-pagamentos/ ; Febraban, golpe do QR Code https://www1.folha.uol.com.br/mercado/2026/10/golpe-do-qr-code-pode-desviar-pagamentos-por-boleto-e-pix-veja-como-se-proteger.shtml

Segurança, nuvem, aplicativos e IA

- F20 [A] OWASP: ASVS 5.0.0 https://github.com/owasp/asvs ; Top 10:2025 https://top10.owasp.org/2025/0x00_2025-Introduction/ ; API Security Top 10 2023 https://owasp.org/API-Security/editions/2023/en/0x11-t10/ ; Top 10 para LLM 2025 https://genai.owasp.org/resource/owasp-top-10-for-llm-applications-2025/
- F21 [A] IETF: RFC 9457 https://www.rfc-editor.org/rfc/rfc9457.html ; RFC 8594 https://www.rfc-editor.org/rfc/rfc8594.html ; RFC 9745 https://www.rfc-editor.org/rfc/rfc9745.html ; RFC 9700 https://www.rfc-editor.org/rfc/rfc9700.html ; RFC 9449 https://www.rfc-editor.org/rfc/rfc9449.html ; rascunho de cabeçalhos RateLimit https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/
- F22 AWS: [V] regiões, uma única no Brasil (`sa-east-1`, 3 AZs; 34 regiões listadas) https://docs.aws.amazon.com/global-infrastructure/latest/regions/aws-regions.html ; [A] cofre lógico isolado do AWS Backup https://docs.aws.amazon.com/aws-backup/latest/devguide/logicallyairgappedvault.html ; [A] KMS com chave externa https://docs.aws.amazon.com/kms/latest/developerguide/keystore-external.html ; [A] WAF, CAPTCHA e Bot Control https://docs.aws.amazon.com/waf/latest/developerguide/waf-captcha-and-challenge-best-practices.html ; [A] RDS com muitos objetos https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.HighObjectCount.html ; [A] guia prescritivo multi-tenant https://docs.aws.amazon.com/prescriptive-guidance/latest/saas-multitenant-managed-postgresql/matrix.md ; [A] Azure https://learn.microsoft.com/en-us/azure/reliability/regions-list ; [A] Oracle https://www.oracle.com/cloud/public-cloud-regions/
- F23 Lojas e Capacitor: [V] Apple, diretriz 4.2.6 https://developer.apple.com/app-store/review/guidelines/ ; [A] inscrição, isenção de taxa e D-U-N-S https://developer.apple.com/programs/enroll/ e https://developer.apple.com/help/account/membership/fee-waivers/ ; [A] requisitos de SDK https://developer.apple.com/news/upcoming-requirements/ ; [A] Google Play, API alvo https://support.google.com/googleplay/android-developer/answer/11926878 e conta de organização https://support.google.com/googleplay/android-developer/answer/13628312 ; [A] Capacitor https://capacitorjs.com/docs/main/reference/support-policy ; [A] fim do Ionic Appflow https://ionic.io/blog/important-announcement-the-future-of-ionics-commercial-products ; [A] contrato de licença do programa de desenvolvedor, seção 3.3.2 https://developer.apple.com/support/terms/apple-developer-program-license-agreement/
- F24 [A] IA e ANPD: PL 2338/2023 https://www.camara.leg.br/proposicoesWeb/fichadetramitacao?idProposicao=2487262 ; ISO/IEC 42001 https://www.iso.org/standard/42001 ; regulamentos da ANPD https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd ; Res. CD/ANPD 15/2024 https://www.in.gov.br/en/web/dou/-/resolucao-cd/anpd-n-15-de-24-de-abril-de-2024-556243024 ; guia de segurança para agentes de pequeno porte https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-sobre-seguranca-da-informacao-para-agentes-de-tratamento-de-pequeno-porte
- F25 [A] PostgreSQL em modo transação: https://www.pgbouncer.org/config.html ; https://jdbc.postgresql.org/documentation/server-prepare/ ; https://github.com/pgjdbc/pgjdbc/issues/3896

Fontes internas citadas por caminho: `docs/levantamento/10-regulatorio-lgpd-privacidade.md` (Marco Civil art. 15, Res. ANPD 15/2024, 18/2024, 19/2024), `13-regulatorio-atendimento-acessibilidade-assinaturas.md` (AS-01, AS-03, AS-06), `editais/18a` (TR-027, TR-028, TR-061, TR-126), `editais/18b` (linha 454, produto concorrente), `editais/18c` (SG-685 a SG-702, SG-1256), `editais/18h` (M80, M81), `gsan/20e` (lacuna 8).

### 1.4 Limites da pesquisa

Os domínios da Meta recusaram conexão a partir da máquina de pesquisa; páginas do DOU bloqueiam leitura automática; a Lei 14.063 foi lida em espelho; as páginas primárias de Gmail, Yahoo e Microsoft não foram abertas; não foi localizada API pública nacional da CENPROT, de birôs de crédito nem regra da Anatel para SMS de remetente alfanumérico. Cada ponto está tratado na seção 5.

## 2. Quadro-resumo dos achados

Prioridade e esforço são sugestões. "Param." indica se vira parâmetro do tenant (ADR-0038).

| ID | Classe | Achado | Prior. | Esf. | Encaixe | Param. |
|---|---|---|---|---|---|---|
| PLT-01 | A | API pública de integração: credencial por tenant, escopos, cota, ambiente de teste e ciclo de vida | alta | G | Nova, depois da P1.11; ajusta ED-62, ED-17 | Sim |
| PLT-02 | A | Eventos e webhooks de saída para o tenant, com entrega confiável e assinatura | média | M | Nova, sobre a outbox (P1.9); ajusta ED-07 | Sim |
| PLT-03 | A | Restauração seletiva de um tenant e desfazer de operação em massa | alta | G | Nova, junto de P0.28, P3.16 e GL.3; ajusta LC.3 | Não |
| PLT-04 | A | Desastre regional em território nacional: decisão, cofre isolado e cópia em segundo provedor | média | M | Decisão do dono; ajusta LC.3, RNF-14, RNF-15, ADR-0025 | Sim |
| PLT-05 | A | Domínio próprio do tenant (CNAME, certificado automático, proteção contra tomada de subdomínio) | média | M | Nova, junto de P1.19 e LC.2; ajusta MARCA-E-TEMAS §3.3 | Sim |
| PLT-06 | A | Login gov.br no portal, com as restrições reais de elegibilidade (ver GES-32) | baixa | M | Ajusta P3.3 e P0.38 | Sim |
| PLT-07 | A | Assinatura eletrônica dos documentos do consumidor e do tenant, por nível da Lei 14.063 | média | G | Nova, junto de P3.20; ajusta P4.1, ED-19, ED-23, ED-38 | Sim |
| PLT-08 | A | Baseline de segurança de produto: ASVS 5.0, API Top 10 e LLM Top 10 como critério de aceite | alta | P | Ajusta RNF-30 a RNF-39, P3.8, LC.8, ED-73 | Não |
| PLT-09 | A | Divulgação responsável de vulnerabilidade e Trust Center público | média | P | Nova, junto de P0.44 e P3.18 | Não |
| PLT-10 | A | Entregabilidade e mensageria por tenant: e-mail, SMS e WhatsApp (ver GES-19) | alta | M | Ajusta ED-41, ED-42, P3.5, LC.2 | Sim |
| PLT-11 | A | Proteção contra cobrança falsa: verificação pública de autenticidade e canais oficiais | alta | M | Nova, junto de P3.20 e P2.8; ajusta P3.2 | Sim |
| PLT-12 | A | Central de notificações do usuário e preferências | média | M | Nova, junto de ED-07; usa P1.9 e P1.18 | Sim |
| PLT-13 | A | Caixa unificada de pendências e aprovações, com delegação temporária | média | G | Nova, antes de P3.30; ajusta P3.1, P2.20, P1.27, P2.22 | Sim |
| PLT-14 | A | Fuso horário e calendário operacional do tenant | média | P | Ajusta P0.19, P2.1, P3.4 | Sim |
| PLT-15 | A | Busca global, atalhos de teclado, visões salvas e densidade | média | M | Nova, junto de P1.12 e P1.18 | Não |
| PLT-16 | A | Framework declarativo de migração por legado | alta | G | Ajusta P1.2, P2.10, P2.19, ED-09 | Não |
| PLT-17 | A | Medição de uso e custo por tenant, e base comprovável de ligações ativas | média | M | Ajusta ADR-0019, P2.12, ED-10, Central | Sim |
| PLT-18 | A | Plataforma de IA: operação de modelos, segurança e aceite da ED-43 (ver GES-08) | alta | G | Ajusta ED-43; precondição de qualquer modelo | Sim |
| PLT-19 | A | Exportação e integridade verificável da trilha de auditoria do tenant | média | M | Ajusta P1.7, ED-06, ED-73 | Sim |
| PLT-20 | A | Hub de integrações: saúde, credenciais, reprocessamento e teste de contrato | alta | G | Nova, junto de P0.45 e P3.15 | Sim |
| PLT-21 | A | Jornada de implantação visível ao cliente e gates de prontidão | média | M | Ajusta P1.6, ED-09, ED-11 | Não |
| PLT-22 | A | Defesa contra automação e abuso no portal, compatível com WCAG 3.3.8 | média | M | Ajusta P3.3, P3.8 | Sim |
| PLT-23 | A | Prática de resiliência: exercícios de falha, alerta ligado a runbook, revisão pós-incidente | baixa | P | Ajusta P3.11, LC.7 | Não |
| PLT-24 | A | Validação cadastral e detecção de óbito do titular | baixa | M | Ajusta P1.3, P1.31 | Sim |
| PLT-25 | A | Internacionalização mínima e idioma de preferência | baixa | P | Ajusta P0.34, P1.18 | Sim |
| PLT-26 | B | Webhook Pix: mTLS e confirmação, no lugar de "assinatura do PSP" | alta | P | Ajusta ARQUITETURA-ALVO §2, RNF-06, P3.2 | Não |
| PLT-27 | B | Calendário regulatório do débito automático e da cobrança híbrida (ver GES-15) | alta | P | Ajusta P0.30, P3.24, ED-27, P3.2 | Sim |
| PLT-28 | B | Capacidade do Keycloak e do realm do portal | média | P | Ajusta RNF-17, P0.23, P2.9, ARQUITETURA-ALVO §6 | Não |
| PLT-29 | B | SSO corporativo (OIDC e SAML) e SCIM por tenant, além de LDAP/AD | média | M | Ajusta ED-06, P0.38 | Sim |
| PLT-30 | B | Política de senha e MFA alinhada ao NIST 800-63B-4; passkey no portal | baixa | P | Ajusta ADR-0040, ED-06, P3.3 | Sim |
| PLT-31 | B | Acessibilidade: WCAG 2.2 AA, declaração por tenant e Libras | média | P | Ajusta RNF-60, P0.36, P3.3 | Sim |
| PLT-32 | B | Cardinalidade e custo das métricas por tenant | média | P | Ajusta RNF-50, RNF-51, P0.8 | Não |
| PLT-33 | B | Plataformas móveis: prazos de loja, contas de organização e atualização remota | média | P | Ajusta ADR-0026, ED-18, ED-39, P3.14 | Sim |
| PLT-34 | B | Incidente de segurança e contrato de operador (Res. ANPD 15/2024) | média | P | Ajusta P3.9, P3.10, Central LGPD | Sim |
| PLT-35 | B | Esquema por tenant: testes de upgrade, catálogo e instrução preparada | média | P | Ajusta P0.21, P2.2, P0.9 | Não |
| PLT-36 | B | Tarifa social: origem da lista conforme a natureza jurídica do prestador | média | P | Ajusta ED-57, P1.5 | Sim |
| PLT-37 | B | Cadeia de suprimentos: proveniência, fixação de versões e endurecimento do CI | baixa | P | Ajusta RNF-35, P0.3 | Não |
| PLT-38 | C | Detecção de anomalia no acesso a dados pessoais (comportamento do usuário) | média | M | Ajusta ADR-0008, RNF-41, Central Segurança | Sim |
| PLT-39 | C | Clone mascarado do tenant para homologação e treinamento | média | M | Ajusta ED-09, ED-11, P0.45 | Não |
| PLT-40 | C | Segunda via e Pix em modo de contingência estático quando a API cai | média | M | Ajusta RNF-11, P3.3, P2.18 | Sim |
| PLT-41 | C | Chave do cliente (BYOK e HYOK) opcional | baixa | M | Ajusta RNF-32, LC.4, ADR-0025 | Sim |
| PLT-42 | C | Telemetria de produto com privacidade (adoção e funil de implantação) | baixa | M | Ajusta Central, P0.8, P4.8 | Sim |

## 3. Detalhamento por achado

Cada achado traz: descrição, por que importa, o que o plano já tem, onde encaixa, prioridade e esforço, parâmetro do tenant e fontes. Quando existe achado equivalente em 22b (OPE) ou 22d (GES), o texto indica a referência e trata só do ângulo de plataforma.

### 3.1 Classe A: ausentes do plano

#### PLT-01 API pública de integração (A)

**Descrição.** Produto de integração para terceiros, distinto da API do frontend: cliente OAuth 2.0 por tenant (`client_credentials`, com `private_key_jwt` ou mTLS; DPoP opcional), escopos no mesmo catálogo `recurso:ação` do ADR-0040 (`properties:read`, `bills:read`, `service-orders:write`), cota e limite de taxa por cliente com 429 e `Retry-After`, lista de IPs permitidos, ambiente de teste com massa sintética, documentação gerada do OpenAPI, tela de gestão de credenciais no tenant (criar, girar, revogar, último uso) e política de ciclo de vida com cabeçalhos `Deprecation` e `Sunset`. Erros em `application/problem+json` conforme a RFC 9457, que obsoleta a RFC 7807.

**Por que importa.** A VISAN exige APIs REST em JSON compatíveis com a e-PING (TR-028) e integração com os demais sistemas do órgão (TR-027), ambos obrigatórios. O 18h marca a integração como "não planejado" (M81) e limita a M80 a "APIs dos demais módulos à medida que nascem". O levantamento 20e (lacuna 8) já registrava a ausência de API pública versionada e de webhook de evento. A e-PING segue como referência de 2018 e só é obrigatória para o SISP [A: F09], de modo que REST e JSON versionados atendem na prática. As credenciais de integração são alvo recorrente de abuso: a RFC 9700 (BCP de segurança do OAuth 2.0) e o OWASP API Security Top 10 (BOLA, propriedade de objeto, consumo irrestrito, fluxos sensíveis, inventário) viram critério de teste.

**O que o plano já tem.** P0.10 (`/api/v1` e OpenAPI), P1.11 (`Idempotency-Key`, ETag), P0.13 (catálogo de permissões), P0.16 e RNF-26 (limites por tenant, usuário e IP), P1.6 (planos, cotas e flags), CENTRAL-ZIIP §8 (autenticação de serviço, só para métricas), ED-62 (conectores com ERP), ED-17 (API de telemetria), ED-44 (consulta para URA). Não há credencial de integração do tenant, cota por cliente nem ambiente de teste.

**Onde encaixa.** Tarefa nova depois da P1.11 e da P1.6, com primeira entrega pequena (leitura de `properties`, `bills`, `payments`, `service-orders`, como propõe o 20e) e ampliação a cada módulo; ajusta ED-62 e ED-17 para usarem a mesma camada. Cota de API no plano do contrato (ADR-0019, item 2). Teste de segurança pelo PLT-08.

**Prioridade e esforço.** Alta, porque o item é obrigatório em edital; G no total, M para a primeira entrega. O 22d (GES-11) a classifica como média por ângulo comercial; aqui sobe por obrigação de edital.

**Parâmetro do tenant.** Sim: `api.enabled` (classe d, desligada até o contrato prever), `api.rate_limit_per_minute` (classe c, só reduz, teto da plataforma), `api.ip_allowlist` (classe d), `api.client_max` (classe d).

**Fontes.** 18a TR-027 e TR-028; 18h M80 e M81; 20e lacuna 8; F09, F21.

#### PLT-02 Eventos e webhooks de saída para o tenant (A)

**Descrição.** Catálogo versionado de eventos (`bill.issued`, `payment.settled`, `service_order.closed`, `protocol.updated`, `connection.cut`, `connection.reconnected`) entregue por HTTPS a endpoints cadastrados pelo tenant. Entrega com semântica de pelo menos uma vez, `event_id` único, ordem por chave de agregado, assinatura HMAC-SHA-256 com carimbo de tempo (proteção contra repetição) e rotação de segredo com sobreposição, tentativas com recuo exponencial e jitter, desativação automática após falhas consecutivas com alerta, tela de entregas com reenvio por 30 dias e proteção contra SSRF (somente HTTPS, bloqueio de endereços privados e de redirecionamento, resolução de DNS fixada no envio). Carga útil sem dado pessoal, só identificadores (20e, lacuna 8).

**Por que importa.** Integrações com prefeitura, cobrança terceirizada (ED-68), gráfica, ERP e sistemas de campo de terceiros hoje só seriam possíveis por arquivo. A experiência com o Pix mostra o risco inverso: o Banco Central não assina o callback (PLT-26), então o Ziip, ao emitir eventos, deve assinar.

**O que o plano já tem.** Outbox (ADR-0014, P1.9), entradas de webhook (P3.2, ED-42), canal de webhook para alertas da Central (CENTRAL-ZIIP §3, aba Alertas e Config.), ED-07 (alerta ao usuário, sem saída para sistema externo). Webhook de saída para o tenant: nenhum.

**Onde encaixa.** Tarefa nova sobre a outbox, com tabela `webhook_endpoint` e `webhook_delivery`; ED-07 passa a usar o mesmo mecanismo para "evento de negócio configurado pelo tenant". Painel de entregas na área do tenant e contagem de falhas na Central (PLT-20).

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `api.webhooks_enabled` (classe d), `api.webhook_max_endpoints` (classe d), `api.webhook_event_types` (classe d).

**Fontes.** 20e lacuna 8; F10 (ausência de assinatura no padrão do Pix); F21; F20 (SSRF figura na API Top 10 e no Top 10:2025).

#### PLT-03 Restauração seletiva de um tenant e desfazer de operação em massa (A)

**Descrição.** Procedimento e ferramenta para devolver um único tenant a um instante anterior, sem afetar os demais do cluster. O PITR do RDS restaura o cluster inteiro; com cerca de 50 tenants por cluster (RNF-20), um erro de operação em um tenant (importação errada, cancelamento em lote, parâmetro mal aceito, exclusão indevida) não pode ser corrigido voltando o cluster. Caminho proposto: restaurar o PITR em instância lateral, extrair o schema do tenant (`pg_dump -n`, ou por `tenant_id` no schema compartilhado), comparar com o estado atual, aplicar a substituição ou a mesclagem em janela, com aprovação do tenant e registro na trilha. Complemento: cópia lógica diária do schema de cada tenant em armazenamento com retenção imutável, que também alimenta a exportação de fim de contrato (P3.16) e o pacote de reversão (ED-04). Para lote, operação reversível em duas etapas (simulação e aplicação), com identificador de execução (`run_id`) e remoção por execução.

**Por que importa.** É o cenário de recuperação mais provável em SaaS multi-tenant e não aparece em nenhuma meta. RNF-14 a RNF-16 e GL.3 medem restauração do banco inteiro.

**O que o plano já tem.** PITR e restauração cronometrada (P0.28, GL.3, P4.7, LC.3, LC.12); teste de RLS no banco restaurado; `pg_dump -n` citado só para eliminação no ADR-0005; correção de efeitos de negócio por OS inversa (ADR-0039) e auditoria por `run_id` (P2.3). A OS inversa corrige regra de negócio, não dado corrompido.

**Onde encaixa.** Tarefa nova no P3, depois da P3.16: ensaio de restauração de um tenant sintético com RTO por tenant registrado (sem meta antes da LC.3), runbook, papel `PLATFORM_OWNER` com step-up, aprovação do tenant (como o consentimento do ADR-0020) e cópia lógica diária no escopo da LC.5. RNF novo: "RTO de restauração de um tenant".

**Prioridade e esforço.** Alta; G.

**Parâmetro do tenant.** Não: procedimento da plataforma. A retenção das cópias lógicas segue o RNF-43 (expiração em até 35 dias) e o crypto-shredding.

**Fontes.** F22 (guia prescritivo e RDS sobre muitos objetos tratam o custo de upgrade e backup por schema); ARQUITETURA-ALVO §3; RNF-14 a RNF-16.

#### PLT-04 Desastre regional em território nacional (A)

**Descrição.** Hoje a recuperação cobre falha de zona (Multi-AZ, RTO de 4 h) e nenhum backup ou réplica sai de `sa-east-1` (LC.3, RNF-44). Nada trata a perda da região inteira. A AWS tem uma única região no Brasil, com 3 zonas, e nenhuma segunda anunciada [V: F22]; a cópia entre regiões da AWS sairia do país, o que o TR-061 da VISAN veda ("armazenamento ou espelhamento de dados em datacenters situados fora do país"). Opções a decidir por cartão: (a) aceitar Multi-AZ como DR e declarar a limitação no contrato e no nível Setor Público (ADR-0025); (b) acrescentar o cofre lógico isolado do AWS Backup, com Vault Lock em modo de conformidade, em conta separada, disponível em `sa-east-1` [A]; (c) cópia cifrada e imutável dos backups, com a KEK sob controle do Ziip, em segundo provedor com região no Brasil (Azure Brazil South e Southeast; Oracle Brazil East e Southeast) [A]; (d) pilot-light em segundo provedor. O pacote de reversão do ADR-0024 (stack em contêineres sobre dados exportados) é um caminho de recuperação a cru e pode ser reaproveitado em (c).

**Por que importa.** O contrato do nível Setor Público (ADR-0025) promete disponibilidade de 99,9%; sem texto sobre desastre regional, a promessa tem um silêncio que o edital pode explorar. Ransomware e erro de credencial administrativa também atingem backups na mesma conta e região; o cofre isolado reduz esse risco.

**O que o plano já tem.** ADR-0011, ADR-0033, LC.3 (snapshots em outra conta, na mesma região), RNF-14, RNF-15, RNF-44, ADR-0024 (reversão), ADR-0010 (exceções de infraestrutura).

**Onde encaixa.** Decisão do dono (cartão) e depois ajuste da LC.3; ADR novo ou adendo ao ADR-0011; exceção no ADR-0010 se houver segundo provedor.

**Prioridade e esforço.** Média; M (a decisão pesa mais que a obra).

**Parâmetro do tenant.** Sim, por nível de serviço contratado: `dr.second_provider_copy` (classe d, ligada no nível Setor Público) e declaração da limitação no contrato. A residência é decisão da plataforma (RNF-44).

**Fontes.** F22; 18a TR-061; RNF-44; ADR-0025.

#### PLT-05 Domínio próprio do tenant (A)

**Descrição.** O tenant publica o portal e a área de trabalho em domínio próprio (`agua.cidade.sc.gov.br`), por CNAME para a borda do Ziip, com certificado emitido e renovado automaticamente, validação de posse do domínio, remoção do apontamento ao encerrar o contrato (proteção contra tomada de subdomínio por registro DNS órfão), mapeamento de `Origin`, CSP, `redirect_uri` do OIDC e `__Host-` por host, e o mesmo domínio disponível para o remetente de e-mail com DKIM alinhado (PLT-10).

**Por que importa.** O MARCA-E-TEMAS §3.3 adota só `<slug>.<tenantSubdomainBase>`. Autarquias municipais preferem o domínio institucional; o login gov.br exige que o serviço esteja em domínio GOV.BR [V: F01] (PLT-06); e o PLT-11 depende de o consumidor reconhecer o endereço oficial. Limites de domínios alternativos por distribuição de CDN e o desenho de certificados para 300 tenants precisam ser vistos na LC.2 [INFERÊNCIA: conhecimento prévio da cota de nomes alternativos do CloudFront, não verificada aqui].

**O que o plano já tem.** Subdomínio por tenant (P1.19), tema do Keycloak gerado do pacote (P0.35), CSP com hash (P0.16, P0.36), BFF com cookie `__Host-` (P0.14), IaC com CloudFront e WAF (LC.2). Domínio próprio: nenhum.

**Onde encaixa.** Tarefa nova junto da P1.19 para o modelo (`tenant_domain`, estados, verificação por registro TXT) e da LC.2 para a automação de certificados; teste de tomada de subdomínio no P3.8.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `brand.custom_domain` (classe d, com verificação de posse).

**Fontes.** MARCA-E-TEMAS §3.3; F01.

#### PLT-06 Login gov.br no portal, com as restrições reais (A)

**Descrição.** Opção de entrar com gov.br no realm `ziipsan-portal`, por intermediação de provedor de identidade OIDC no Keycloak, para operações de maior risco (tarifa social, negociação, troca de titularidade), exigindo o nível prata ou ouro pelo catálogo de confiabilidades. Sobreposição: GES-32 (22d) trata do mesmo tema como inovação de prioridade baixa; este achado acrescenta o que a fonte primária confirma.

**Por que importa.** Fato confirmado em 22/10/2026 [V: F01]: a integração é exclusiva de órgãos e entidades públicos; o solicitante é gestor público com conta prata ou ouro; o sistema deve estar hospedado em domínio GOV.BR; o município deve ter aderido à Rede Nacional de Governo Digital; cada serviço exige solicitação própria. Logo, vale só para tenant autarquia ou órgão, nunca para tenant privado, e depende do domínio próprio (PLT-05). Credenciais de produção, `id_token` de 60 segundos e PKCE S256 [A: F02]. A assinatura avançada do gov.br segue a mesma regra (PLT-07).

**O que o plano já tem.** P3.3 (login local por CPF, CNPJ ou e-mail, sem SMS), P0.38 (Organizations), ADR-0022, 13 AS-01 e AS-03.

**Onde encaixa.** Ajuste da P3.3 e da P0.38 (identity brokering por tenant) como opção; fica atrás do PLT-05.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `portal.idp_govbr_enabled` (classe d, só tenant de direito público), `portal.idp_govbr_min_level` (classe d: `SILVER` ou `GOLD`). Sem a opção, o login local permanece.

**Fontes.** F01, F02, F03; 22d GES-32.

#### PLT-07 Assinatura eletrônica dos documentos do consumidor e do tenant (A)

**Descrição.** Serviço de assinatura do produto, com três níveis da Lei 14.063/2020: simples (aceite com evidências: hash do texto, data, IP, autenticação, o que o produto já faz em `document_acceptance`), avançada e qualificada. Documentos candidatos: termo de parcelamento (P4.1), contrato de adesão (P3.20, NR 11 art. 18), laudo e aceite da OS em campo (ED-19, ED-23), autodeclaração da tarifa social (P1.5), autorização de débito. O nível mínimo é fixado por tipo de documento e por tenant (Lei 14.063, art. 5º atribui a cada ente) [A: F05]. Assinatura qualificada por certificado em nuvem de PSC credenciado, em PAdES com política `AD-RB` ou superior (DOC-ICP-15.03, versão 9.1 de 2025; IN ITI 33/2025 e 34/2025) e carimbo do tempo de ACT credenciada (SERPRO, entre as dez listadas) por protocolo RFC 3161 (agosto de 2001, atualizada pela RFC 5816, de abril de 2010) [A: F04] (verificado em 22/10/2026: a IN ITI 33/2025 trata de novas versões das políticas de assinatura digital e da versão 9.0 do DOC-ICP-15.03, e não de assinatura em nuvem nem de carimbo do tempo; o DOC-ICP-15.03 trata das políticas de assinatura, e o regime dos prestadores de serviço de confiança está no DOC-ICP-17 (Res. CG ICP-Brasil 180/2020) e no DOC-ICP-17.01 (IN ITI 20/2020); a expressão AD-RB ou superior deve ser conferida contra a tabela de políticas do documento na implementação). Verificação pública por código QR que aponta para página de validação do Ziip e para o VALIDAR do ITI [A: F04]. A assinatura avançada do gov.br só é acessível a gestor público e a serviço em domínio gov.br (API sob as mesmas condições do Login Único) [A: F03], portanto não é caminho para o Ziip como entidade privada.

**Por que importa.** O levantamento 13 (AS-01, AS-03, AS-04) já recomendava o nível definido pelo tenant; nenhuma tarefa do plano o assumiu. O 18h (SG-1398) exige contrato assinado por ICP-Brasil ou gov.br para a habilitação, parte coberta pelo e-CNPJ do P0.44. Para os documentos do consumidor, o aceite simples com prova costuma bastar, mas a escolha deve ser do tenant.

**O que o plano já tem.** `document_acceptance` com hash e IP (CENTRAL-ZIIP §4, P1.24), e-CNPJ da Ziip/TGP (P0.44, P1.8), cofre de certificado do motor fiscal (ADR-0003), armazenamento imutável (P2.18). Nada para assinatura do usuário.

**Onde encaixa.** Tarefa nova depois da P3.20; interface `DocumentSigner` com implementações simples e PSC; a ED-19 usa o simples com captura de rubrica no aparelho. Documentos de entrega ao tenant (DPA, contrato) usam o serviço só se o dono decidir.

**Prioridade e esforço.** Média; G.

**Parâmetro do tenant.** Sim: `sign.min_level.<document_type>` (classe d: `SIMPLE`, `ADVANCED`, `QUALIFIED`), `sign.psc_provider` (classe d), `sign.timestamp_authority` (classe d). A aplicação da Lei 14.063 a relações entre privados não foi verificada [INFERÊNCIA]; por isso o nível é opção do tenant.

**Fontes.** F03, F04, F05; 13 AS-01, AS-03, AS-04; 18h SG-1398.

#### PLT-08 Baseline de segurança de produto: ASVS, API Top 10 e LLM Top 10 (A)

**Descrição.** Declarar o OWASP ASVS 5.0 como critério de aceite de segurança: nível 2 para backoffice, portal, API e BFF; nível 3 para isolamento de tenant, cofre de chaves, console da plataforma e acesso assistido. Mapear os capítulos às RNF-30 a RNF-39 e aos casos da P3.8 e da LC.8. Acrescentar o OWASP API Security Top 10 2023 ao teste da API pública (PLT-01) e o Top 10 para LLM 2025 ao aceite da ED-43 (PLT-18). Registrar o OWASP Top 10:2025, que traz falhas na cadeia de suprimentos de software (A03) e tratamento de condições excepcionais (A10) como categorias novas [A: F20].

**Por que importa.** O plano cita OWASP só pelo ZAP (P3.8, RNF-34). O ASVS 5.0.0 (maio de 2025) tem 345 requisitos em três níveis [A: F20] e fornece lista verificável para pentest, ISO/IEC 27001 (ED-73) e habilitação em edital. Custo baixo, ganho alto de rastreabilidade.

**O que o plano já tem.** RNF-30 a RNF-39, P0.24 (STRIDE), P3.8 (DAST), LC.8 (pentest), ED-73.

**Onde encaixa.** Ajuste de redação dos critérios, sem tarefa nova: ASVS como anexo do `REQUISITOS-NAO-FUNCIONAIS.md`, com matriz requisito, teste e evidência gerada no CI; decisão do dono sobre os níveis.

**Prioridade e esforço.** Alta; P.

**Parâmetro do tenant.** Não.

**Fontes.** F20.

#### PLT-09 Divulgação responsável e Trust Center público (A)

**Descrição.** Política pública de divulgação de vulnerabilidade com arquivo `/.well-known/security.txt` (RFC 9116 [INFERÊNCIA: norma conhecida, não reaberta nesta análise]), canal, prazo de resposta e salvaguarda legal; e uma página de confiança com: lista de suboperadores e aviso de mudança (DPA, P3.9), certificações do provedor (AWS Artifact, P0.26), resumo do pentest, status (P3.18), política de privacidade e de retenção, encarregado (Res. ANPD 18/2024), resposta padrão a questionários de segurança e evidências do ASVS (PLT-08).

**Por que importa.** Compradores públicos pedem evidências na habilitação e na POC; o `kit de habilitação` (P0.44) hoje é um pacote fechado. Pesquisa de segurança sem canal costuma virar divulgação pública.

**O que o plano já tem.** P0.44, P0.26, P3.9, P3.18 (status público), ADR-0021 (Sobre com documentos), `AJUDA-SUPORTE-SOBRE.md` §9.

**Onde encaixa.** Tarefa nova pequena junto da P3.18 (mesma hospedagem estática separada da produção).

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Não.

**Fontes.** F20; RNF-36; ADR-0020.

#### PLT-10 Entregabilidade e mensageria por tenant (A)

**Descrição.** Arquitetura de envio por canal, hoje indefinida (ADR-0033: "serviço de e-mail definido na IaC (LC.2)"). E-mail: usar o recurso de tenants do Amazon SES, que isola métricas e reputação por cliente, aplica política de pausa só ao tenant afetado, mantém supressão por tenant e painel de entregabilidade por tenant, disponível em `sa-east-1` [A: F14]; domínio de envio por tenant com SPF, DKIM e DMARC alinhados (PLT-05); tratamento de rejeição e reclamação que marca o contato como inválido no cadastro e abre pendência, e que conta como "aviso não entregue" na regra de prova da P3.5. Requisitos de Gmail, Yahoo e Microsoft para remetente em massa (SPF, DKIM e DMARC, taxa de reclamação abaixo de 0,10%) [S: F15]. WhatsApp: catálogo de modelos por tenant e categoria, fila de aprovação, monitoramento de qualidade, verificação da assinatura do webhook, política de uso de IA (PLT-18); preço por mensagem desde 01/07/2025 e mudanças a partir de 01/10/2026 [A/S: F13]. SMS: não foi encontrada regra da Anatel específica para remetente alfanumérico; há medida provisória recente, a MP 1.396/2026 (de 09/10/2026), sobre autenticação, identificação e bloqueio de chamadas telefônicas e mensagens e base de dados centralizada de recursos de numeração [S].

**Por que importa.** A prova de entrega do aviso de corte bloqueia a OS (P3.5); um canal com reputação ruim compromete a conformidade da NR 11 e a cobrança. Um tenant com lista suja não pode derrubar o envio dos demais. Custo e teto de mensagens são tratados em GES-19 (22d), fora deste achado.

**O que o plano já tem.** ED-41 (e-mail, SMS, WhatsApp, push com opt-in por canal e finalidade; provedores no DPA), ED-42 (omnichannel), P3.5 (prova de entrega), MARCA-E-TEMAS §6 (SPF, DKIM, DMARC do remetente do produto), P0.45 (interface de e-mail), ED-10 (repasse de mensagens). Não há tratamento de rejeição, supressão, domínio por tenant nem política de modelos.

**Onde encaixa.** Ajuste da ED-41 e da ED-42, tarefa nova de "entregabilidade" na onda A/B (junto de P3.5) e decisão do provedor de e-mail na LC.2; contrato de interface em P0.45 com os eventos `delivered`, `bounced`, `complained`.

**Prioridade e esforço.** Alta; M.

**Parâmetro do tenant.** Sim: `msg.email_from_domain` (classe d), `msg.channels_enabled` (classe d), `msg.suppression_policy` (classe b), `msg.whatsapp_template_set` (classe d), `msg.sms_sender_id` (classe d).

**Fontes.** F13, F14, F15; 22d GES-19.

#### PLT-11 Proteção contra cobrança falsa (A)

**Descrição.** Conjunto de medidas contra boleto adulterado, QR Code trocado, WhatsApp falso e site clonado: (1) página pública de verificação de autenticidade da fatura (código curto ou QR na conta que leva ao portal e confere valor, vencimento, recebedor e hash do documento); (2) lista de canais oficiais do tenant na conta, no portal e nos e-mails (reaproveita `branding.contact`); (3) no Pix, nome do recebedor e CNPJ conferíveis no aplicativo do banco, com orientação impressa e teste de que o recebedor cadastrado no PSP é a razão social do tenant; (4) política DMARC rígida e remetente fixo por tenant (PLT-10); (5) mensagem de verificação por texto padrão nos canais (o Ziip nunca pede pagamento por link avulso); (6) denúncia de golpe pelo portal que alimenta alerta na Central.

**Por que importa.** Em outubro de 2026 a Saneago, a BRK e a Febraban publicaram alertas de golpe por boleto, QR Code e WhatsApp em nome de concessionárias de água [S: F19]. O plano resolve a 2ª via e o Pix, mas não dá ao consumidor meio de verificar a origem; o risco recai sobre a marca do tenant e sobre o Pix recebido em conta errada. Sem documentação de autenticidade, o consumidor não distingue.

**O que o plano já tem.** P2.8 (código de vinculação na conta), P3.20 (DANFAG em PDF e XML com hash, histórico de 120 meses), P3.2 (QR Pix), P3.3 (portal), MARCA-E-TEMAS §3 (contato e rodapé do tenant), ADR-0022. Verificação pública de autenticidade: nenhuma.

**Onde encaixa.** Tarefa nova depois da P3.20, com página `GET /api/v1/public/documents/verify`; ajuste da P2.8 (leiaute da conta) e da P3.2 (conferência do recebedor). Telemetria de verificações na aba Segurança da Central.

**Prioridade e esforço.** Alta; M.

**Parâmetro do tenant.** Sim: `portal.official_channels` (classe d, lista de canais oficiais), `portal.verify_enabled` (classe d, padrão ligado).

**Fontes.** F19; 18f (atendimento); ADR-0022.

#### PLT-12 Central de notificações do usuário (A)

**Descrição.** Caixa de notificações dentro da aplicação (sino, lista, lido e não lido), alimentada pela outbox, com preferências por usuário: canais (aplicação, e-mail, push), resumo diário ou imediato, silenciar por período e categorias obrigatórias que não se desligam (segurança, aprovação pendente). Sustenta ED-07 (alerta ao usuário por evento de negócio), a notificação de revogação e de sessão assistida (ADR-0020), alertas de lote e a caixa do PLT-13.

**Por que importa.** O plano cita alerta ao usuário (ED-07) sem dizer onde ele aparece. O frontend atual teve um sino com contador falso removido (`frontend-cadastro.md`).

**O que o plano já tem.** Outbox (P1.9), preferências do usuário no servidor (P1.18, `ui.theme.*`), ED-07, notificações de chamado (ADR-0020), ED-41 (canais para o consumidor, não para o operador).

**Onde encaixa.** Tarefa nova junto da ED-07, usando `user_preferences` (P1.18) e a outbox; fica na onda A.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `notify.mandatory_categories` (classe c, piso da plataforma), `notify.digest_default` (classe d).

**Fontes.** ED-07; ADR-0020.

#### PLT-13 Caixa unificada de pendências e aprovações, com delegação temporária (A)

**Descrição.** Componente comum de tarefas de aprovação: `approval_task` (tipo, objeto, solicitante, aprovadores elegíveis, prazo, estado) e tela única "Minhas pendências". Substitui telas dispersas por módulo: aprovação dupla do retorno bancário (P3.1), alçada de requisição de material (ADR-0032, ED-58), retificação de conta (P2.20), aprovação por tipo de serviço (P1.31), catálogo do GIS (P1.27), pacote de edição (P2.22), OS e protocolo. Delegação temporária com início e fim (férias, afastamento), nunca maior que a permissão do delegante, auditada; escalonamento por prazo; regra de segregação (quem solicita não aprova) aplicada pelo componente.

**Por que importa.** O plano tem pelo menos seis fluxos de aprovação, cada um com sua tela. A ADR-0040 trata a delegação de permissão pelo ADMIN, não a substituição temporária do aprovador; sem ela, a ausência de um aprovador trava o ciclo. O relatório de "pendências por usuário" (POC-65, P3.22) é leitura, não ação.

**O que o plano já tem.** Aprovações pontuais citadas acima; ADR-0040 (permissões `recurso:ação`, escopo por área P3.30); P2.20 (aprovação conforme parâmetro); ED-06 (perfis compostos). Nenhum motor comum; o 18f (SG-525) registra workflow como "não planejado".

**Onde encaixa.** Tarefa nova antes da P3.30 para criar o componente e migrar P3.1 e P2.20 sem mudar regra; as demais aprovações usam o componente quando nascem.

**Prioridade e esforço.** Média; G.

**Parâmetro do tenant.** Sim: `wf.delegation_max_days` (classe d), `wf.escalation_hours` (classe d), `wf.self_approval` (classe a, sempre `FALSE`).

**Fontes.** ADR-0032, ADR-0040; 18f SG-525.

#### PLT-14 Fuso horário e calendário operacional do tenant (A)

**Descrição.** `tenant.timezone` (identificador IANA, padrão `America/Sao_Paulo`) aplicado a datas de negócio, janelas de corte e religação (`cut.business_hours`), calendário de lote, SLAs úteis, vencimentos e agendamento no `worker`. Armazenar instantes em UTC e datas de negócio como data local do tenant. Teste com tenants de fusos diferentes (`America/Manaus`, `America/Rio_Branco`, `America/Noronha`).

**Por que importa.** O plano só fixa `America/Sao_Paulo` no motor fiscal (`dhEmi`, FISCAL-PORTE-JAVA §2) e fala em "domingo 0h–4h (Brasília)" para manutenção (RNF-12). O Brasil tem quatro fusos oficiais; tenants no Amazonas, Acre e Mato Grosso têm a janela legal de corte, o limite de horário de atendimento e o vencimento deslocados em uma ou duas horas. Fim do horário de verão desde 2019 [INFERÊNCIA: conhecimento prévio, não reaberto].

**O que o plano já tem.** `cut.business_hours`, `cal.holidays` (PARAMETROS-DO-TENANT), P2.1 (calendário do ciclo), P3.4 (janela de corte), RNF-12 (manutenção). Fuso do tenant: nenhum.

**Onde encaixa.** Ajuste da P0.19 (parâmetro) e dos critérios de P2.1, P3.4, SLA (P2.15) e agendamentos do `worker`; teste do CI com relógio simulado em dois fusos.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `tenant.timezone` (classe d, padrão de fábrica `America/Sao_Paulo`).

**Fontes.** RNF-12; FISCAL-PORTE-JAVA §2.

#### PLT-15 Busca global, atalhos, visões salvas e densidade (A)

**Descrição.** Produtividade do operador: (1) busca global por teclado (Ctrl/Cmd+K) sobre cliente, unidade, protocolo, OS e conta, respeitando permissão, escopo (P3.30) e a cota de busca por documento (`sec.document_search_quota`, ADR-0008), de modo que a busca global não vire contorno do controle de CPF; (2) atalhos com remapeamento e desligamento (WCAG 2.1.4, atalhos de tecla única); (3) visões salvas de filtros e colunas por usuário e compartilhadas por unidade; (4) densidade da tabela (confortável, compacta); (5) ações recentes e favoritos, que o GSAN já oferecia (`usuario_favoritos`).

**Por que importa.** A rotina de atendimento e cobrança lida com muitas consultas por dia; o GSAN tinha favoritos e atalhos (levantamento 04 e 07). O 18g (SG-1272) pede busca global no BI. Hoje só há "filtro preservado" ao voltar da ficha (P1.12).

**O que o plano já tem.** P0.22 (índice trigram), P1.12 (filtro preservado, tabelas no celular), P1.18 (preferências no servidor), P0.36 (temas), RNF-60.

**Onde encaixa.** Tarefa nova no P3, junto do portal de operação; estende `user_preferences` (P1.18).

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Não: preferência do usuário. Limites da busca seguem os parâmetros `sec.*` existentes.

**Fontes.** 07 (atalho `/`), 04 (favoritos), 18g SG-1272; ADR-0008; F08 (WCAG 2.1.4 é critério da 2.1 em nível A, conhecimento prévio).

#### PLT-16 Framework declarativo de migração por legado (A)

**Descrição.** Camada reutilizável acima do importador do piloto: leitores de fonte (dump SQL, CSV, banco legado), mapeamento declarativo versionado em arquivo (campo de origem, transformação, validação, destino), staging com perfil de qualidade (completude, duplicidade, integridade referencial, documento inválido), ensaio sem gravar (dry run), conciliação automática por setor, rota, categoria e saldo, relatório de linhagem por registro e reexecução idempotente. Cada legado novo passa a ser um arquivo de mapeamento mais testes, não um importador novo.

**Por que importa.** A P1.2 foi desenhada como "importador v0 do cadastro" do piloto; a ED-09 (onda C) prevê "importador do legado do primeiro cliente de edital (GSAN, se confirmado)". Com meta de 300 tenants e legados diversos (o 18b cita o produto Sansys, de concorrente, e a ED-62 cita Betha, IPM e Fiorilli), cada migração custaria um projeto. A VISAN exige linhagem de dados (TR-026) e migração completa com validação quantitativa (TR-010 a TR-018 e 18h M87).

**O que o plano já tem.** P1.1 (discovery), P1.2, P2.10 (120 meses), P2.19 (XML anteriores), GL.1, LC.10, ED-09 (dicionário, linhagem, simulação em 60 dias), P0.45 (varredura de dado pessoal).

**Onde encaixa.** Ajuste do critério da P1.2 para que o desenho do importador seja o framework (custo marginal baixo se feito agora, alto depois), e da ED-09 para publicar o formato de mapeamento; sem tarefa nova.

**Prioridade e esforço.** Alta; G (acréscimo de M sobre a P1.2).

**Parâmetro do tenant.** Não: o mapeamento é artefato do projeto de implantação de cada tenant.

**Fontes.** 18a TR-026; 18h M87; PLANO P1.2 e ED-09.

#### PLT-17 Medição de uso e custo por tenant (A)

**Descrição.** Registro de uso por tenant e por mês: ligações ativas, usuários, armazenamento, relatórios gerados, chamadas de API (PLT-01), mensagens por canal e categoria, consumo de modelo de linguagem, minutos de lote. Dois usos: (1) base contratual comprovável: fotografia mensal de ligações ativas por tenant, com hash, relatório entregue ao cliente e janela de contestação (o preço da VISAN é proporcional ao número de ligações de água, 18b); (2) custo por tenant: atribuição de custo do provedor por etiqueta e por proxy (tamanho de schema, mensagens, tokens), mostrando margem na aba Contratos da Central. Sobreposição parcial com GES-19 (22d, teto de gasto de mensagens); este achado cobre o ledger único e a base de faturamento.

**Por que importa.** ADR-0019 prevê cotas de ligações ativas, usuários e armazenamento, mas não define como se mede nem como o cliente contesta. A ED-10 monta a planilha de preço por porte sem medição de custo real por tenant.

**O que o plano já tem.** ADR-0019 (cotas e alertas a 80% e 100%), P2.12 (contrato, faturas, reajuste), ED-10, ED-42 (mensagens medidas), CENTRAL-ZIIP §3 (aba Contratos).

**Onde encaixa.** Ajuste da P2.12 e da aba Contratos; tabela `usage_ledger` no schema `platform` sem dado pessoal.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `quota.*` por contrato (já previstas), `usage.statement_contest_days` (classe d).

**Fontes.** ADR-0019; 18b (modelo de preço de concorrente); 22d GES-19.

#### PLT-18 Plataforma de IA: operação de modelos, segurança e aceite da ED-43 (A)

**Descrição.** Camada comum para qualquer função com modelo de linguagem ou aprendizado: registro de modelo e de prompt com versão; conjunto de conversas de referência executado no CI a cada mudança (regressão de qualidade e de segurança); proteções contra injeção de instrução, vazamento de dado sensível, agência excessiva e consumo ilimitado (itens do OWASP Top 10 para LLM 2025); mascaramento antes do envio ao provedor; registro de cada conversa com dado mascarado e retenção própria; cota e teto de custo por tenant; chave geral de desligamento por tenant e por função; escolha de provedor com processamento no Brasil ou transferência internacional amparada na Res. CD/ANPD 19/2024; vedação de treinar com dado de tenant. Complementa a política do GES-08 (22d), que fixa as vedações (nenhuma decisão exclusivamente automatizada sobre corte, negativação, tarifa social). O art. 20 da LGPD garante revisão de decisão automatizada; o PL 2338/2023 aguardava parecer do relator na comissão especial da Câmara em 21/10/2026 [A: F24], de modo que hoje valem LGPD e a leitura da ANPD.

**Por que importa.** A ED-43 lista aceites de chamada de funções e minimização, mas o SAMAE (SG-685 a SG-701) exige também detecção de sentimento, humano no circuito para corrigir respostas, identificação de intenção nova, busca semântica, mecanismo de comportamento anômalo (SG-699), mascaramento inteligente (SG-700) e auditoria da conversa (SG-701). Meta proíbe, desde 15/01/2026, provedor de IA que ofereça assistente de propósito geral como função principal; a Meta diz que bot de atendimento de empresa não é afetado [A/S: F13], e o CADE mantém medida cautelar [A]. O fornecedor da ED-43 deve ficar no escopo de atendimento do prestador.

**O que o plano já tem.** ED-43 (funções fechadas, texto sem CPF, transbordo humano, DPA e RIPD), ADR-0028 (c), RNF-44 (exceção de provedor de modelo no RIPD), P3.9, P3.10. Nenhum registro de modelo, avaliação nem desligamento.

**Onde encaixa.** ADR de IA (GES-08) e tarefa de plataforma antes da ED-43; critérios de aceite da ED-43 ampliados com os itens acima. ISO/IEC 42001 pode entrar no catálogo voluntário do ADR-0041 como norma ativável [A: F24].

**Prioridade e esforço.** Alta como precondição; G.

**Parâmetro do tenant.** Sim: `ai.enabled_features` (classe b, padrão desligado), `ai.provider_region` (classe c), `ai.log_retention_days` (classe c), `ai.human_review_required` (classe a, travado para decisões de corte, negativação e tarifa social).

**Fontes.** F20, F21, F24; 18c SG-685 a SG-701; 22d GES-08.

#### PLT-19 Exportação e integridade verificável da trilha de auditoria do tenant (A)

**Descrição.** (1) Exportação contínua da trilha do tenant (`audit_log` e `personal_data_access_log`) para o SIEM do cliente, em JSON por linha ou CEF, por entrega em armazenamento do cliente ou fluxo HTTPS assinado. (2) Prova de integridade: encadeamento por hash ou raiz de Merkle diária por tenant e partição, gravada em armazenamento com retenção imutável e verificada por tarefa agendada, no mesmo modelo já previsto para `platform.audit_event` (CENTRAL-ZIIP §10).

**Por que importa.** A trilha do tenant é "só inserção" (levantamento 17), mas sem prova externa de que não foi alterada por quem tem acesso privilegiado; a trilha da plataforma já tem hash encadeado. A ISO/IEC 27001 (ED-73) pede registro de eventos protegido, e editais pedem consulta da trilha por usuário (TR-128, SG-062, ED-06). Busca por `SIEM`, `syslog`, `CEF` no plano: nenhum resultado.

**O que o plano já tem.** P1.7 (partição, UUIDv7, retenção com piso), P2.3 (auditoria por lote), ED-06 (consulta da trilha por usuário), `HashChain` do XML (P2.18), `audit_event` encadeado (CENTRAL-ZIIP §4, §10), RNF-41, RNF-52.

**Onde encaixa.** Ajuste da P1.7 (raiz diária) e tarefa nova de exportação na onda A, depois da ED-06; usa o padrão de objetos imutáveis da LC.5.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `log.export_enabled` (classe d), `log.export_format` (classe d). O conteúdo exportado não inclui dado pessoal em claro (RNF-52).

**Fontes.** CENTRAL-ZIIP §10; ED-73; 18a TR-128.

#### PLT-20 Hub de integrações (A)

**Descrição.** Contrato comum para todo conector externo (PSP, bancos, SEFAZ e NFS-e, WhatsApp, e-mail, SMS, birôs, CENPROT, gráfica, ERP): credenciais por tenant no cofre, com rotação e alerta de expiração; teste de conexão; limites de taxa e circuito aberto; política padrão de tempo limite e repetição; idempotência; fila de falhas com tela de reprocesso; correlação por `correlation_id`; log com carga mascarada; modo de simulação e de homologação por tenant; estado de saúde por integração e por tenant exposto na Central (aba "Integrações") e no console do tenant; teste de contrato diário contra o ambiente de homologação do terceiro para detectar mudança de leiaute.

**Por que importa.** O plano trata cada integração dentro da sua tarefa (fiscal, Pix, retorno, mensageria), sem camada comum. O módulo `integration` do código está sem POM (levantamento 17 §2). Com 300 tenants, o operador precisa ver que 12 tenants estão com o certificado do PSP vencido ou com a fila de WhatsApp parada sem abrir cada módulo.

**O que o plano já tem.** P0.45 (interfaces de infraestrutura: armazenamento, chaves, segredos, fila e e-mail), `FiscalDocumentIssuer`, `pay.pix_provider` com credencial no cofre, CENTRAL-ZIIP abas Fiscal e Pix, alerta de certificado a vencer, mensagens `DEAD` (aba Plataforma), Radar de normas.

**Onde encaixa.** Tarefa nova junto da P3.15 (Central completa), com porta `ExternalConnector` e a aba Integrações; as integrações novas (ED-30, ED-33, ED-41, ED-42, ED-62, ED-68) já nascem sobre ela.

**Prioridade e esforço.** Alta; G.

**Parâmetro do tenant.** Sim: `integration.<name>.mode` (`SIMULATED`, `SANDBOX`, `PRODUCTION`, classe d, produção com aceite), `integration.<name>.credential_ref` (classe d).

**Fontes.** 17 §2; CENTRAL-ZIIP §3; ADR-0033.

#### PLT-21 Jornada de implantação visível ao cliente e gates de prontidão (A)

**Descrição.** Painel do cliente com o checklist de implantação (parâmetros pendentes de aceite, importação e conciliação, certificado fiscal, teste com o PSP, política de MFA, restauração ensaiada, encarregado informado, DPA assinado, treinamento) e porcentagem concluída; o mesmo checklist alimenta gates automáticos de "pronto para produção" na Central, reaproveitando o Health Score. Conteúdo do checklist vem do manifesto do pacote de vertical (ADR-0037).

**Por que importa.** O assistente da P1.6 é do operador. O prazo de implantação é critério de edital (60 e 90 dias, 18h M84) e depende de pendências que são do cliente; visibilidade compartilhada reduz idas e vindas.

**O que o plano já tem.** P1.6 (assistente de implantação, produção bloqueada sem parâmetros aceitos), CENTRAL-ZIIP §6.1, ED-09 (plano de 60 e 90 dias), ED-11 (treinamento), `onboarding_step` (CENTRAL-ZIIP §4), P3.13.

**Onde encaixa.** Ajuste da P1.6 (visão do tenant sobre `onboarding_step`) e da ED-09.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Não.

**Fontes.** 18h M84; CENTRAL-ZIIP §4.

#### PLT-22 Defesa contra automação e abuso no portal (A)

**Descrição.** Camadas sobre o limite por tentativa e o bloqueio progressivo da P3.3: regras gerenciadas de bot na borda e desafio não cognitivo (prova de trabalho, ou desafio invisível) no lugar de quebra-cabeça; detecção de preenchimento credencial (um IP com muitos CPFs, um CPF com muitos IPs); atraso crescente em vez de bloqueio da conta (para a vítima não ser travada de fora); verificação de senha contra lista de vazadas no cadastro; limite de criação de conta por e-mail; telemetria para a Central. O CAPTCHA e o desafio do AWS WAF só rodam em requisições GET com HTML; em API e aplicativo é preciso o SDK de integração [A: F22]. O critério 3.3.8 da WCAG 2.2 (autenticação acessível) proíbe teste de função cognitiva sem alternativa [A: F08], o que descarta CAPTCHA de imagem.

**Por que importa.** O portal tem vínculo por matrícula e documento, enumerável por natureza (API6 e API4 do OWASP). O 04 sugeria hCaptcha ou Turnstile para o portal; nenhuma tarefa adotou.

**O que o plano já tem.** P3.3 e P3.20 (tentativas limitadas por identificador, conta e IP, bloqueio progressivo, resposta indistinta, 429, alerta), P3.8 (casos de enumeração), RNF-26, ADR-0008 (cota), CENTRAL-ZIIP aba Segurança.

**Onde encaixa.** Ajuste da P3.3 e da P3.8; regra na LC.2 (WAF).

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `portal.bot_defense` (`OFF`, `PASSIVE`, `CHALLENGE`, classe d), `portal.account_throttle` (classe c, só reduz).

**Fontes.** F08, F20, F22; 04 (captcha do GSAN).

#### PLT-23 Prática de resiliência (A)

**Descrição.** Exercícios periódicos de falha (derrubada de réplica, perda de PSP, indisponibilidade da SEFAZ, fila parada, certificado vencido) em homologação e, depois da LC, em produção controlada; `runbook_url` e responsável em cada `alert_rule`; modelo de revisão pós-incidente sem culpado, publicado em resumo na página de status; revisão trimestral de SLO e orçamento de erro.

**Por que importa.** A P3.11 prova que "falha provocada dispara alerta"; não há rotina. Sem ligação alerta e procedimento, o MTTR depende de memória.

**O que o plano já tem.** P3.11 (runbook, plantão, burn rate), RNF-53, P3.18, LC.7, CENTRAL-ZIIP §3 e §4 (`alert_rule` com dono).

**Onde encaixa.** Ajuste da P3.11 e do modelo `alert_rule`.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Não.

**Fontes.** RNF-53, RNF-54.

#### PLT-24 Validação cadastral e detecção de óbito do titular (A)

**Descrição.** Consulta de situação cadastral de CPF e CNPJ na entrada do cadastro (P1.3), com cache e custo medido por tenant (PLT-17). Opções confirmadas [A: F18]: APIs de Consulta CPF e CNPJ e Datavalid do Serpro, contratadas por pessoa jurídica com e-CNPJ, preço por faixa; a Consulta CPF informa óbito [S]; dados de registro civil (SIRC, Decreto 9.929/2019, art. 7º) cedidos a órgão ou entidade pública em pedido motivado, vedado repassar a terceiro, de modo que só tenant de direito público poderia solicitar. Resultado "óbito" gera OS interna de troca de titularidade (ADR-0039) e suspende acréscimos conforme `bill.*`; resultado "irregular" gera alerta, não bloqueio.

**Por que importa.** Cobrança contra titular falecido e documento inválido são fontes recorrentes de reclamação e de dívida irrecuperável; a validação reduz erro na entrada, mas exige base legal (LGPD art. 7º) e custo por consulta.

**O que o plano já tem.** Máscara e busca controlada (ADR-0008), validação de dígito (P1.3), importadores com relatório (P1.2). Nenhuma consulta externa de situação.

**Onde encaixa.** Ajuste da P1.3 como integração opcional pelo hub (PLT-20); decisão do dono sobre custo e base legal.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `reg.cpf_validation_provider` (classe d), `reg.deceased_check` (classe d, só direito público para SIRC).

**Fontes.** F18.

#### PLT-25 Internacionalização mínima e idioma de preferência (A)

**Descrição.** Catálogo de mensagens externalizado desde o início (as chaves `nameKey` e `descriptionKey` do manifesto já fazem isso para a vertical), formatação de data, número e moeda por `Intl`, e preferência de idioma do usuário com pt-BR como único idioma publicado. Sem tradução agora; apenas evitar texto fixo no código.

**Por que importa.** O SAMAE (SG-1256) pede escolha de idioma de preferência, "inclusive o Português (Brasil)", afetando interface e ajuda. A solução barata é estrutural.

**O que o plano já tem.** Chaves de i18n no manifesto (PLATAFORMA-E-VERTICAIS §3), pacote de marca com `texts/pt-BR.json` (MARCA-E-TEMAS §2.1), ajuda em Markdown em português.

**Onde encaixa.** Ajuste da P0.34 e da P1.18 (campo `ui.locale`); sem tarefa nova.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Sim, trivial: `ui.default_locale` (classe d, padrão `pt-BR`).

**Fontes.** 18c SG-1256; 18g.

### 3.2 Classe B: presentes, mas rasos

#### PLT-26 Webhook Pix: mTLS e confirmação, no lugar de "assinatura do PSP" (B)

**Descrição.** O desenho diz que a API "valida a origem e a assinatura do PSP" (ARQUITETURA-ALVO §2 item 4) e a P3.2 exige "webhook assinado". Na especificação do Banco Central (`openapi.yaml` 2.10.0, lida por mim), o callback `POST {webhookUrl}/pix` é declarado sem esquema de segurança e a descrição informa apenas que o canal é protegido por mTLS; não há assinatura, HMAC nem cabeçalho de assinatura no padrão [V: F10]. Pontos adicionais confirmados: só Pix associado a `txid` é notificado (QR estático e Pix sem `txid` não geram callback); o SLA do callback e a política de reenvio ficam a cargo de cada PSP; o registro é `PUT /webhook/{chave}` e o padrão não define envio de certificado, que depende do PSP [A: F10]. Mecanismos extras (segredo na URL, HMAC, lista de IPs) são recurso de cada PSP [S].

**Por que importa.** Aceite por "assinatura" não é testável com o padrão; o risco é aceitar callback forjado ou, ao contrário, descartar o legítimo.

**O que o plano já tem.** Caixa de entrada idempotente por `(psp, end_to_end_id)`, resposta 200 em menos de 2 s, conciliação pelo `worker` e consulta diária de recuperação (ARQUITETURA-ALVO §2 item 4), RNF-06, `pay.pix_provider` (tenant do webhook vem do convênio).

**Onde encaixa.** Ajuste do critério de aceite da P3.2 e do texto do ARQUITETURA-ALVO: (1) mTLS com certificado do PSP e, onde o PSP oferecer, segredo adicional; (2) identificação do tenant e do convênio por segmento aleatório na URL; (3) confirmação do `endToEndId` por `GET /pix/{e2eid}` antes da baixa, quando o PSP não oferecer mecanismo equivalente; (4) varredura periódica de `GET /pix` (`inicio` e `fim`) para Pix sem `txid` e para callbacks perdidos; (5) matriz de comportamento por PSP (reenvio, SLA, certificado) como parte da interface de PSP (PLT-20); (6) teste com callback forjado, repetido e fora de ordem.

**Prioridade e esforço.** Alta; P.

**Parâmetro do tenant.** Não: segurança do recebimento é decisão da plataforma; a credencial e o PSP seguem em `pay.pix_provider`.

**Fontes.** F10.

#### PLT-27 Calendário regulatório do débito automático e da cobrança híbrida (B)

**Descrição.** Fatos confirmados: desde 13/10/2025, a autorização de débito em conta cujo recebedor final seja pessoa jurídica ou entidade não autorizada a funcionar pelo BCB deve seguir exclusivamente o Pix Automático, com ressalva quando o banco do pagador é também o destinatário; o prazo de adequação foi 01/01/2026, exceto contratos e autorizações de tributos, de "convênios de prestação de serviços públicos" e de planos de saúde, até 01/01/2027 (Res. CMN 4.790, art. 2º-A, §§ 1º e 2º, com a Res. CMN 5.251/2025 e a Res. CMN 5.278/2025, que prorrogou o prazo do § 2º) [V: F12]. O voto do CMN cita expressamente os órgãos públicos e concessionárias de serviços públicos como motivo do prazo adicional [V: F12] (verificado em 22/10/2026: o registro oficial vincula o Voto 92/2025-CMN à Res. CMN 5.278/2025, mas o PDF do voto não foi localizado na verificação; a regra deve ser citada pela resolução, e não pelo voto). A cobrança híbrida (boleto e QR do Pix Cobrança no mesmo documento) fica regulada a partir de 01/02/2027: vale só para Pix Cobrança com vencimento, boleto comum ou dinâmico sem ativo financeiro, emissor igual ao PSP do recebedor, oferta facultativa, e o Pix Cobrança é cancelado quando o boleto é pago [V: F11].

**Consequência para o plano.** A P3.24 (FEBRABAN 150 para autorizações legadas, condicional a optantes) e a parte legada da ED-27 só ocorrerão depois do lançamento (outubro de 2028, opção A do §2.4) [INFERÊNCIA sobre a data]; nessa data, a autorização interbancária de débito em conta com recebedor concessionária já estará sujeita ao Pix Automático. O legado FEBRABAN 150 só tende a subsistir quando banco do pagador e banco do recebedor forem a mesma instituição [INFERÊNCIA: leitura do art. 2º-A, § 1º]. O 22d (GES-15) cita a Res. BCB 544/2025 como origem do prazo de 01/01/2027; a resolução que li com esse prazo é a CMN 5.278/2025; as duas referências devem ser conferidas pelo dono no cartão (verificado em 22/10/2026: ambas estão corretas, cada uma para um tipo de conta: a Res. CMN 5.278/2025 cobre contas de depósitos e conta-salário, e a Res. BCB 544/2025, de 24/12/2025, traz a mesma prorrogação para contas de pagamento pré-pagas, no § 2º do art. 2º-A da Res. BCB 51/2020; citar as duas).

**O que o plano já tem.** P0.30 (levantamento de optantes), P3.24, ED-27 ("data de desligamento do legado parametrizada conforme a regra vigente do Banco Central"), P4.5 (Pix Automático), P3.2 (Pix Cobrança e boleto híbrido), o quadro "Prazos legais do prestador" do §5 (linha 1209 do plano), que lista o boleto híbrido em 01/02/2027 e não lista o prazo de 01/01/2027 do débito automático.

**Onde encaixa.** Ajuste do aceite da P0.30 (perguntar, por optante, se o banco do pagador é o mesmo do recebedor), da P3.24 (condição "só optante do mesmo banco") e da P3.2 (regras da cobrança híbrida testadas: cancelamento do Pix ao pagar o boleto, recusa de boleto de proposta e de depósito). As regras operacionais do Pix Automático (retentativa de até 3 datas em 7 dias, janela de 2 a 10 dias antes da liquidação, jornadas 1 a 4) já estão em GES-15; não são repetidas.

**Prioridade e esforço.** Alta; P.

**Parâmetro do tenant.** Sim: `pay.legacy_debit_enabled` (classe d), com a data de desligamento deduzida da regra, não digitada; opções do Pix Automático em GES-15.

**Fontes.** F11, F12; 22d GES-15; PLANO P3.24 e ED-27.

#### PLT-28 Capacidade do Keycloak e do realm do portal (B)

**Descrição.** O dimensionamento inicial (ARQUITETURA-ALVO §6: 2 réplicas de 1 vCPU e 2 GB) não tem meta de logins nem de renovações por segundo. O guia oficial indica cerca de 15 logins por senha por segundo e por vCPU, 120 concessões `client_credentials` por segundo por vCPU, 120 renovações de token por segundo por vCPU, 1.250 MB por réplica com 10.000 sessões em cache, e carga de banco de 1.400 IOPS de escrita mais 0,35 a 0,7 vCPU por 100 operações de login, logout ou renovação por segundo [A: F06]; o benchmark oficial de outubro de 2025 mediu 2.000 logins por segundo e 10.000 renovações por segundo com 3 réplicas, quase linear [A: F06]. Com token de acesso de 5 minutos nos aplicativos (RNF-39), 100 mil sessões ativas gerariam cerca de 333 renovações por segundo (cálculo meu, [INFERÊNCIA]), ou seja, cerca de 3 vCPU só para renovar. O autenticador próprio do portal localiza a conta pelo índice cego; seu custo em tabela de atributos de milhões de usuários precisa de medição. Não há orientação oficial sobre realm único com milhões de usuários [A: F06].

**O que o plano já tem.** RNF-17 (2+ réplicas, `start --optimized`), P0.29, LC.6, P0.23 e P2.9 (carga de referência da API), ADR-0022 (autenticador próprio).

**Onde encaixa.** Ajuste do RNF-17 e da P0.23/P2.9: cenário de carga do Keycloak com o kit de benchmark oficial (logins, renovações e `client_credentials` do PLT-01), com meta de pico do portal no vencimento e na emissão da conta, e medição do autenticador próprio com base sintética de 1 milhão de usuários no realm do portal.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Não.

**Fontes.** F06.

#### PLT-29 SSO corporativo (OIDC e SAML) e SCIM por tenant (B)

**Descrição.** A ED-06 prevê federação LDAP/AD por organização. Muitos órgãos municipais usam serviços de nuvem de e-mail e identidade (Microsoft 365, Google Workspace) [INFERÊNCIA]; o caminho natural é provedor de identidade externo por OIDC ou SAML vinculado à organização do tenant, com opção de tornar o SSO obrigatório e de provisionar usuários por SCIM. O Keycloak 26.8.0 (01/10/2026) promoveu a API SCIM nativa a suportada, habilitada por realm, apenas de entrada (recebe usuários e grupos), sem operações em lote, ordenação, ETag nem troca de senha [V: F06]; Organizations é suportado desde a 26.0 e na 26.8 permite compartilhar provedor de identidade entre organizações [A: F06]. Em realm único, a API SCIM é do realm; o isolamento entre tenants exige permissões administrativas finas por organização e teste de cruzamento [INFERÊNCIA: a documentação lida não afirma isolamento por organização].

**O que o plano já tem.** ED-06 (LDAP/AD, perfis compostos, sessões visíveis), P0.38 (avaliação de Organizations), ADR-0040 (realm único, delegação limitada), ADR-0007 (MFA por tenant), 21a (recertificação, ED-73).

**Onde encaixa.** Ajuste da ED-06 e da P0.38: prova de conceito com dois tenants, provedor externo por organização, regra "SSO obrigatório" e, em segundo passo, SCIM com escopo por organização e teste de que o token de um tenant não lê nem altera usuário de outro (mesmo teste exigido a cada atualização do Keycloak pelo ADR-0040, item 5).

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `sec.sso_mode` (`LOCAL`, `OIDC`, `SAML`, `LDAP`, classe d), `sec.sso_enforced` (classe d), `sec.scim_enabled` (classe d). O provisionamento por SCIM não concede papel que o ADMIN do tenant não possua.

**Fontes.** F06.

#### PLT-30 Política de senha e MFA frente ao NIST 800-63B-4; passkey no portal (B)

**Descrição.** O `PARAMETROS-DO-TENANT.md` já fixa mínimo de 12 caracteres com lista de senhas vazadas, expiração desligada e histórico de 5 (ADR-0040). A revisão 4 final do NIST 800-63B (31/07/2025) exige comparação com lista de bloqueio, proíbe exigir troca periódica sem evidência de comprometimento, proíbe regras de composição e fixa mínimo de 15 caracteres quando a senha é fator único e 8 com MFA; permite passkey sincronizável em AAL2 [A: F07]. Portanto: (1) a "complexidade" da ED-06 e a expiração por exigência de edital (POC-05) devem ser opções marcadas no console como contrárias à recomendação, com aceite; (2) para contas de portal sem MFA, o mínimo sobe a 15 ou a conta é incentivada a usar passkey; (3) o Keycloak suporta passkeys desde a 26.4.0 [A: F06]; ADR-0007 já prevê WebAuthn no backoffice, mas o portal (ADR-0022) só prevê MFA opcional sem método definido.

**O que o plano já tem.** ADR-0007, ADR-0040, P0.15, ED-06, ADR-0022, P3.3, RNF-30.

**Onde encaixa.** Ajuste do ADR-0040 (nota de alinhamento), do console (aviso na parametrização) e da P3.3 (passkey no portal como método de MFA e de entrada).

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Sim: já existe `sec.password_*`; acrescentar `portal.passkey_enabled` (classe d).

**Fontes.** F06, F07.

#### PLT-31 Acessibilidade: WCAG 2.2 AA, declaração por tenant e Libras (B)

**Descrição.** O RNF-60 fixa WCAG 2.1 AA e eMAG 3.1. O levantamento 13 recomendava WCAG 2.2 AA como critério de aceite e declaração de acessibilidade por tenant. A WCAG 2.2 é recomendação do W3C desde 05/10/2023 (atualizada em 12/12/2024) e foi adotada como ISO/IEC 40500:2025 (norma do comitê conjunto ISO/IEC JTC 1, de texto igual à versão de outubro de 2023 da WCAG 2.2); o eMAG segue na versão 3.1 de 2014, com base na WCAG 2.0, e o TCU (Acórdão 2099/2025) o chama de desatualizado; existe a ABNT NBR 17225:2025, Acessibilidade em conteúdo e aplicações web (Requisitos), norma brasileira própria que adota a WCAG 2.2 como diretriz internacional (verificado em 22/10/2026: não é tradução da WCAG 2.2; a tradução autorizada para o português é publicação separada do Ceweb.br/NIC.br, de 27/03/2025; a conformidade regular da NBR corresponde ao nível AA; o trecho consultado da NBR cita área de acionamento de 44 px CSS, e o alvo de 24 por 24 px é o critério 2.5.8 da WCAG 2.2, de modo que a afirmação sobre pixels deve ser conferida no texto da NBR) [A: F08]. A 2.2 acrescenta, no nível AA, tamanho mínimo de alvo de 24 por 24 pixels (2.5.8), autenticação acessível (3.3.8: permitir gerenciador de senha e colar, sem teste cognitivo) e foco não encoberto (2.4.11), entre outros; afeta diretamente o login por CPF e a defesa do PLT-22. O VLibras é gratuito, de código aberto (LGPL-3.0) e integrável por script; os termos exigem autorização para uso comercial da marca e do serviço, e a fronteira para sítio privado é ambígua [A: F08].

**O que o plano já tem.** RNF-60 (axe no CI, auditoria manual com NVDA), RNF-63, P0.36 (contraste), P3.20 (PDF acessível), ED-39 (acessibilidade do aparelho), ED-79 (Carta de Serviços).

**Onde encaixa.** Ajuste do RNF-60 para WCAG 2.2 AA (mantendo a menção ao eMAG 3.1 como referência do setor público) e da P0.36/P3.3; tarefa pequena para a declaração de acessibilidade do tenant (canal de contato, data da avaliação) e para o widget VLibras como opção, após consulta formal ao serviço.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `portal.vlibras_enabled` (classe d), `portal.accessibility_statement` (classe d).

**Fontes.** F08; 13 (recomendação); RNF-60.

#### PLT-32 Cardinalidade e custo das métricas por tenant (B)

**Descrição.** O RNF-50 pede "métricas RED por rota e por tenant". Com 300 tenants, 150 rotas, histograma de 12 limites mais soma e contagem, e três classes de status, a conta é 300 × 150 × 14 × 3 ≈ 1,9 milhão de séries para uma única métrica de latência [INFERÊNCIA: número de rotas e de limites assumidos]. Em serviço gerenciado cobrado por série ativa, o custo e a latência de consulta crescem com esse produto.

**O que o plano já tem.** RNF-50, RNF-51 (traces com `tenant_id`, amostragem de 10% mais erros e lentas), P0.8, CENTRAL-ZIIP §5 (resumos por tenant em `metric_point`), LC.7.

**Onde encaixa.** Ajuste do RNF-50 e da P0.8: rótulo de tenant apenas em contadores agregados por grupo de rota e em um conjunto curto de métricas de negócio; latência por rota sem tenant; visão por tenant pelos traces e por `metric_point` (já agregado na Central); orçamento de séries por serviço verificado no CI.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Não.

**Fontes.** RNF-50, RNF-51; cálculo próprio.

#### PLT-33 Plataformas móveis: prazos de loja, contas de organização e atualização remota (B)

**Descrição.** Fatos confirmados: Apple rejeita app criado de modelo comercial ou serviço de geração, exceto quando enviado diretamente pelo fornecedor do conteúdo, e o prestador do serviço não deve submeter em nome dos clientes (diretriz 4.2.6) [V: F23]; o ADR-0026 já prevê a conta do prestador. Conta de organização na Apple exige D-U-N-S, exceto entidade governamental, e há isenção de taxa para entidade governamental; no Google Play a conta de organização exige D-U-N-S, com verificação alternativa para órgão governamental conhecido [A: F23]. Desde 28/04/2026, envios à App Store exigem Xcode 26 e SDK iOS 26, com salto para o SDK 27 em abril de 2027; no Google Play, novos apps e atualizações têm alvo Android 16 (API 36) desde 31/08/2026 [A: F23]. O Capacitor 8 é a linha ativa; a 7 sai do suporte estendido em 08/12/2026; o Ionic Appflow encerra em 31/12/2027 [A: F23]. Atualização remota só de código interpretado é permitida desde que não altere o propósito principal do aplicativo (contrato de licença do programa Apple, 3.3.2) [A: F23].

**O que o plano já tem.** ADR-0026, ADR-0030, ED-18, ED-39 (app por tenant, publicado na conta do prestador), RNF-39, P3.14 (Spring Boot em linha com suporte, sem equivalente para o toolchain móvel).

**Onde encaixa.** Ajuste do ADR-0026 e da ED-39: passo de "abertura das contas de loja do tenant" no checklist de implantação (PLT-21), com prazo de D-U-N-S, e política de manutenção do toolchain móvel nos moldes da P3.14 (versões do Capacitor, API alvo, Xcode); decisão de OTA sem depender do Appflow.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `app.store_account_owner` (classe d, titular da conta de loja do tenant).

**Fontes.** F23.

#### PLT-34 Incidente de segurança e contrato de operador (B)

**Descrição.** A Res. CD/ANPD 15/2024 impõe ao controlador comunicar a ANPD e os titulares em 3 dias úteis, com complemento em 20 dias úteis e registro por 5 anos; o operador informa o controlador sem demora injustificada, e a ANPD recomenda fixar o prazo em contrato [A: F24; levantamento 10, Res. 15/2024, arts. 6º, 9º e 10]. O Ziip é operador dos dados de consumidores; o controlador é o tenant. Não há regulamento publicado da ANPD sobre direitos do titular, RIPD nem medidas de segurança até 21/10/2026 [A: F24].

**O que o plano já tem.** P3.10 (prazos ao controlador e à ANPD, simulação de mesa), P3.9 (DPA), CENTRAL-ZIIP aba LGPD, acesso de emergência com aviso em até 24 h (P3.17), `ret.incident_record_years` de 5 anos (AJUDA-SUPORTE-SOBRE §11), Sobre com encarregado (ADR-0021).

**Onde encaixa.** Ajuste da P3.9 (cláusula de notificação do operador ao controlador, por exemplo em 24 h do conhecimento, valor a decidir [INFERÊNCIA: o texto da norma não fixa número]) e da P3.10 (relógio do incidente iniciado no conhecimento pelo Ziip, alerta na Central a 24 e 48 h, modelo do formulário de comunicação à ANPD e do aviso aos titulares, registro por 5 anos).

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `lgpd.operator_notice_hours` (classe c, só reduz, teto contratual) e `lgpd.dpo_contact` (classe d).

**Fontes.** F24; 10.

#### PLT-35 Esquema por tenant: testes de upgrade, catálogo e instrução preparada (B)

**Descrição.** A P0.21 mede o tempo de migração, o catálogo, o vacuum e a latência com 300 schemas. A documentação da AWS sobre o modelo de schema por tenant aponta catálogo grande, ajuste de vacuum, paradas maiores em atualização de versão e memória elevada por conexão quando o ORM ou o `search_path` guardam metadados de muitos tenants; o RDS diz que não há limite rígido de tabelas e que o limiar de impacto é da ordem de milhões de relações [A: F22]. Com cerca de 300 relações por tenant (76 tabelas e 227 índices, levantamento 17 D2) e 50 tenants por cluster (RNF-20), o desenho fica abaixo desse limiar [INFERÊNCIA]. Falta incluir no teste: tempo de `pg_upgrade` ou de atualização de versão principal e de `pg_dump` por schema com o número alvo; comportamento de instruções preparadas em modo transação do PgBouncer (`max_prepared_statements` desde a 1.21; `prepareThreshold` do pgJDBC; defeito conhecido 3896 do pgJDBC 42.7.8 com `prepareThreshold=0`) [A: F25].

**O que o plano já tem.** P0.21, P0.9, P2.2 (GUC por transação, PgBouncer em modo transação), ADR-0005, ADR-0011 (PgBouncer; RDS Proxy só após teste de pinning).

**Onde encaixa.** Ajuste do aceite da P0.21 e da P2.2 (itens acima) e registro das versões exatas de PgBouncer e driver no `ARQUITETURA-ALVO` §4.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Não.

**Fontes.** F22, F25; 17 D2.

#### PLT-36 Tarifa social: origem da lista conforme a natureza jurídica do prestador (B)

**Descrição.** A ED-57 importa lista de beneficiários "com base legal registrada (sem API pública)". A página da ANA confirma que o regulador infranacional (ERI) pede os dados às coordenações municipais ou estaduais do CadÚnico, processa e repassa ao prestador uma lista mínima; a cessão direta ao prestador ocorre só se ele for órgão ou entidade de direito público, e, mesmo assim, em prestação direta em um único município com acordo prévio [V: F17]. Existe no catálogo do Conecta a API "CADÚNICO – Serviços" (informa se o CPF é baixa renda e se o cadastro foi atualizado em 24 meses), dirigida à administração direta, autárquica e fundacional, mas duas páginas oficiais divergem sobre a disponibilidade para municípios [A: F09]. A tarifa social tem prazo de implementação em 11/12/2026 [A: F17; P1.5 já o usa].

**O que o plano já tem.** P1.5 (R3 satélite), ED-57, ADR-0038 (parâmetros G2), levantamento 10 (decreto 11.016, cessão restrita).

**Onde encaixa.** Ajuste da ED-57: três origens suportadas (lista da ERI, base cedida pela coordenação municipal, consulta Conecta se o tenant tiver aderido), conforme a natureza do prestador; trilha da base legal por lote (já prevista).

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `social.list_source` (`REGULATOR_LIST`, `MUNICIPAL_BASE`, `CONECTA_API`, classe c: a opção depende da natureza jurídica e fica travada contra o que a lei permite).

**Fontes.** F09, F17.

#### PLT-37 Cadeia de suprimentos: proveniência, fixação de versões e endurecimento do CI (B)

**Descrição.** O RNF-35 cobre SBOM, assinatura de imagem, Trivy, `npm audit` e gitleaks. O OWASP Top 10:2025 elevou falhas na cadeia de suprimentos de software a categoria própria (A03) [A: F20]. Itens a acrescentar: ações do GitHub fixadas por hash de commit; federação OIDC do GitHub com a AWS no lugar de chave de longa duração (LC.2); atestados de proveniência da compilação verificados na implantação; arquivo de bloqueio de dependências com revisão de dependência em PR; execução de scripts de instalação de pacotes npm desabilitada por padrão; atraso mínimo para adotar versão nova de dependência, para reduzir exposição a pacotes recém-comprometidos [INFERÊNCIA: práticas gerais, não verificadas em fonte nesta análise].

**O que o plano já tem.** RNF-35, P0.3, P0.5, P0.39 (build reproduzível por versão), P0.40, P0.17 (`maplibre-gl` 6).

**Onde encaixa.** Ajuste da P0.3 e da LC.2.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Não.

**Fontes.** F20.

### 3.3 Classe C: inovações

#### PLT-38 Detecção de anomalia no acesso a dados pessoais (C)

**Descrição.** Linha de base por usuário, por papel e por unidade organizacional sobre `personal_data_access_log`: volume de consultas e de revelações, documentos distintos por hora, horário atípico, picos de exportação, acesso a clientes fora do escopo habitual. Resposta graduada: aviso ao usuário, redução temporária da cota de busca (`sec.document_search_quota`), exigência de step-up e alerta ao ADMIN e ao encarregado do tenant. Estatística simples e explicável antes de qualquer modelo.

**Por que importa.** O plano tem alerta de volume anômalo por usuário e por tenant (ADR-0008) e z-score de falhas de login na Central; falta o comportamento de leitura legítima abusada (consulta em massa de CPF por quem tem permissão). SG-699 (SAMAE) pede detecção de comportamentos anômalos como requisito de segurança de IA.

**O que o plano já tem.** ADR-0008, RNF-41, P0.11, CENTRAL-ZIIP aba Segurança (z-score de login, 429 de busca por documento), ED-06 (sessões e trilha por usuário).

**Onde encaixa.** Ajuste da aba Segurança e do `personal_data_access_log`; alertas ao tenant pela central de notificações (PLT-12).

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `sec.anomaly_response` (`ALERT`, `THROTTLE`, `STEP_UP`, classe d).

**Fontes.** ADR-0008; 18c SG-699.

#### PLT-39 Clone mascarado do tenant para homologação e treinamento (C)

**Descrição.** A mesma rotina de anonimização na origem da P0.45 (código substituto no lugar da matrícula, sem nome, documento, endereço nem contato, coordenada deslocada) é oferecida como função do produto: gerar, a partir da produção do tenant, um tenant de homologação ou treinamento com dado mascarado, marcado como sintético, para testar mudança de parâmetro, treinar usuários e ensaiar integração (PLT-01) sem dado pessoal. Substitui planilhas de teste manuais.

**Por que importa.** A ED-09 prevê promoção de parâmetros entre homologação e produção e a ED-11 prevê tenant de pré-treinamento com massa sintética; o clone mascarado dá realismo (volumes, distribuições) sem violar a minimização.

**O que o plano já tem.** P0.45 (roteiro de anonimização e teste de varredura), P3.23 (massa sintética), ED-09, ED-11, GL.1.

**Onde encaixa.** Tarefa após a ED-09, reaproveitando o roteiro e o teste de varredura; a LGPD exige que a base mascarada não permita reidentificação, com avaliação de risco no RIPD.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Não.

**Fontes.** PLANO P0.45, ED-09, ED-11.

#### PLT-40 Segunda via e Pix em modo de contingência estático (C)

**Descrição.** Quando a `api` ou o banco estiverem indisponíveis, o portal serve a segunda via e o "copia e cola" do Pix a partir de objetos pré-gerados no fechamento de cada conta, cifrados, entregues pela borda mediante link assinado e de curta duração enviado ao titular por e-mail ou mensagem. O Keycloak e a borda continuam independentes. A cobrança híbrida (PLT-27) reforça o formato.

**Por que importa.** A NR 11 exige atendimento 24 por 7 e o RNF-11 mede o portal em 99,9%; a regra de suspensão do SaaS já mantém portal, Pix e retornos com o backoffice somente leitura. O modo estático estende a disponibilidade a falhas da própria aplicação [INFERÊNCIA: viabilidade de arquitetura, não validada].

**O que o plano já tem.** RNF-11, CENTRAL-ZIIP §6.2, P2.18 (armazenamento imutável do XML), P2.8 e P3.20 (PDF e DANFAG), P3.18 (status em hospedagem separada).

**Onde encaixa.** Tarefa pós-P3.20, com cuidado LGPD: objeto com dado pessoal só acessível por link assinado; expiração e revogação.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `portal.static_fallback_enabled` (classe d).

**Fontes.** RNF-11; NR 11 (via 18f).

#### PLT-41 Chave do cliente (BYOK e HYOK) opcional (C)

**Descrição.** Para clientes que exijam controle da chave: importação de material de chave no KMS (suportada em todas as regiões, sem rotação automática, com a disponibilidade do material sob responsabilidade do cliente) ou armazenamento externo de chaves (XKS), que mantém a chave fora da AWS mas não admite chaves multirregião, rotação automática nem material importado em armazenamentos customizados [A: F22]. Revogar a chave equivale a destruição criptográfica imediata e ao mesmo tempo a risco de indisponibilidade; o crypto-shredding (RNF-43) já oferece o efeito de saída.

**O que o plano já tem.** RNF-32, `TenantKeyProvider` (P0.45), LC.4, ADR-0025 (nível Setor Público).

**Onde encaixa.** Só sob demanda de contrato; avaliar na LC.4.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `crypto.key_mode` (`PLATFORM`, `IMPORTED`, `EXTERNAL`, classe d, com aceite do risco de indisponibilidade).

**Fontes.** F22.

#### PLT-42 Telemetria de produto com privacidade (C)

**Descrição.** Medir adoção de funcionalidades, tempo até o primeiro ciclo concluído, abandono de assistentes e tarefas mais repetidas, em agregado por tenant e por papel, sem identificador de pessoa, sem repetição de sessão e sem conteúdo; alimenta a aba "Adoção" da Central, o painel de uso do tenant (ED-07) e a priorização do changelog. Complementa o CSAT e o NPS do P4.8.

**Por que importa.** A Central mede saúde técnica e conformidade, não uso. Sem ele, o Health Score não vê o tenant que parou de usar a conciliação.

**O que o plano já tem.** CENTRAL-ZIIP §3 (Health Score), ED-07 (painel de uso e desempenho), P0.8, RNF-61 (Web Vitals), P4.8.

**Onde encaixa.** Ajuste da Central e do contrato (DPA registra a telemetria); sem tarefa de produto antes da P3.15.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `telemetry.product_usage` (classe d, padrão agregado).

**Fontes.** CENTRAL-ZIIP §3.

## 4. Itens que o plano já cobre bem

Não são repetidos nos achados. Cada linha traz as tarefas ou os documentos que sustentam a avaliação.

| Tema | Onde está | Observação |
|---|---|---|
| Isolamento entre tenants | ADR-0005, RLS FORCE, RNF-38, P0.21, P2.2, P1.14 | Teste de isolamento bloqueia o CI; contexto local à transação; migração em ondas com quarentena |
| Sessão e navegador | ADR-0006, P0.14, P0.16, RNF-31, RNF-34 | Cookie `__Host-`, CSRF, CSP com hash, limites de corpo e de lista |
| Identidade e MFA | ADR-0007, P0.15, P0.43, RNF-30, ADR-0040, P0.13 | MFA por tenant com verificação de `amr`; TOTP e WebAuthn; permissões `recurso:ação` com teste de rota sem declaração |
| Criptografia e chaves | RNF-32, RNF-43, P0.28, LC.4 | AES-GCM por campo com AAD de tenant, índice cego, DEK por tenant, crypto-shredding |
| Trabalho assíncrono | ADR-0014, P1.9, P2.1, RNF-25 | Outbox com `SKIP LOCKED`, fila justa, limite por tenant |
| Operação e Central Ziip | CENTRAL-ZIIP, RNF-50 a RNF-54, P1.17, P3.15 | Coletor com trava e watchdog, Health Score com fatores, trilha encadeada verificada a cada 6 h, API de métricas estável |
| Acesso assistido e de emergência | ADR-0020, P3.17 | Consentimento, vedações, aviso em 24 h, revogação em 1 minuto |
| Saída do cliente | RNF-42, P3.16, ADR-0015, ADR-0024 | Exportação aberta, carência, contestação, crypto-shredding, certificado de descarte, pacote de reversão |
| Ajuda, suporte e status | ADR-0020, ADR-0021, P1.20 a P1.24, P3.18 | Protocolo, SLA por contrato, ClamAV, página de status em hospedagem separada, versão e changelog do build |
| Marca, temas e ícones | ADR-0016, ADR-0017, ADR-0036, P0.34 a P0.37, P0.46 | Pacote validado por esquema, contraste no CI, regra de cor literal |
| Pacotes de vertical | ADR-0037, P0.47 | Manifesto, teste de fronteira, `gis_domain` |
| Parâmetros e limites legais | ADR-0002, ADR-0015, ADR-0038, P0.19, P0.20 | Classes a, b, c, d; piso e teto por norma |
| Aplicativos | ADR-0026, ADR-0030, ADR-0031, RNF-39, ED-18 | PKCE, armazenamento seguro, dispositivo registrado e bloqueio remoto, idempotência por `task_id` |
| Ambiente local e contrato de interfaces | ADR-0033, P0.27, P0.45, LC.1 | Interfaces de infraestrutura com teste de contrato; varredura contra dado pessoal real |
| Retenção e logs | RNF-40, RNF-52, `log.access_retention_days` | Piso de 6 meses do Marco Civil, art. 15; incidente por 5 anos |
| Relatórios e BI | ADR-0027, P3.22, ED-47 | Réplica de leitura, role por tenant no Superset |
| Busca no cadastro | P0.22, RNF-03 | Índice trigram e paginação por chave |
| Certificações de qualidade | ADR-0041, P1.32, ED-69 a ED-76 | Catálogo único de normas ativáveis |
| Protesto e birôs por arquivo | ED-30, ED-68 | Coerente com o que foi confirmado: só portal e arquivo foram localizados para a CENPROT; API nacional pública não foi confirmada [A: F16] |
| Prazo da tarifa social | P1.5 | Já usa 11/12/2026 como marco |

## 5. Inferências e pontos a confirmar

### 5.1 Inferências deste documento

1. [INFERÊNCIA] A P3.24 e a parte legada da ED-27 perdem objeto antes do lançamento (PLT-27): depende de leitura do art. 2º-A, § 1º, da Res. CMN 4.790 e de o lançamento ocorrer em outubro de 2028 (opção A do §2.4).
2. [INFERÊNCIA] O escopo da API SCIM do Keycloak é o realm; o isolamento por organização precisa ser provado na P0.38 (PLT-29).
3. [INFERÊNCIA] Cota de nomes alternativos de distribuição de CDN e desenho de certificados para 300 tenants (PLT-05): conhecimento prévio, a verificar na LC.2.
4. [INFERÊNCIA] Cardinalidade de métricas (PLT-32): 150 rotas e 12 limites de histograma assumidos.
5. [INFERÊNCIA] Fim do horário de verão em 2019 e RFC 9116 (`security.txt`): conhecimento prévio, não reaberto nesta análise (PLT-14, PLT-09).
6. [INFERÊNCIA] Viabilidade do modo estático de contingência (PLT-40) e valor de 24 h para notificação do operador (PLT-34).
7. [INFERÊNCIA] Muitos órgãos municipais usam Microsoft 365 ou Google Workspace (PLT-29).
8. [INFERÊNCIA] Práticas de endurecimento do CI listadas no PLT-37 e WCAG 2.1.4 no PLT-15: conhecimento prévio.

### 5.2 Fatos não confirmados em fonte primária

| Ponto | Situação | Como confirmar |
|---|---|---|
| Preços e regras do WhatsApp a partir de 01/10/2026, isenção para governo, moeda BRL | Só integradores e fonte única; domínios da Meta recusaram conexão | Abrir a página oficial de preços da Meta de outra máquina; consultar o provedor oficial de soluções de negócio |
| Política da Meta para chatbots de IA (15/01/2026) | Texto confirmado em trecho; interpretação favorável a bots de atendimento vem de imprensa | Ler os termos da Business Solution; acompanhar a medida cautelar do CADE |
| SMS: regra da Anatel e medida provisória 1.396/2026 (autenticação, identificação e bloqueio de chamadas e mensagens) | Fontes secundárias; datas divergem (verificado em 22/10/2026: a divergência decorre de a MP ser de 09/10/2026 e de o texto da Presidência indicar publicação no DOU de 13/10/2026; a conversão em lei não foi verificada) | Ler o texto da MP no Planalto e a regulamentação da Anatel |
| Gmail, Yahoo e Microsoft para remetente em massa | Fontes secundárias | Ler as páginas oficiais dos três provedores |
| Conecta gov.br para municípios | Duas páginas oficiais se contradizem | Consulta ao canal do Conecta |
| Domínio GOV.BR para autarquia municipal com domínio próprio | A FAQ exige domínio GOV.BR; exceções não verificadas | Consulta ao Serviço de Integração |
| Aplicação da Lei 14.063 a relações entre privados | Lei lida em espelho; escopo não verificado | Ler o texto no Planalto e opinião jurídica |
| API pública da CENPROT e de birôs | Não confirmada | Contato com o IEPTB e com os birôs |
| Cobertura do AWS Artifact e do WAF (CAPTCHA, Bot Control) em `sa-east-1` | Não confirmada | Páginas "Services in Scope" e tabela regional de serviços |
| Zona local da AWS no Brasil | Não encontrada | Página oficial de zonas locais |
| Res. BCB 544/2025 (citada em GES-15) e Res. CMN 5.278/2025 (lida aqui) para o prazo de 01/01/2027 | Referências diferentes para o mesmo prazo (verificado em 22/10/2026: ambas corretas, cada uma para um tipo de conta; citar as duas) | Conferir o ato no portal de normativos do Banco Central |
| Defeito 3896 do pgJDBC: correção publicada? | Não verificado | Notas de versão do driver |

### 5.3 Decisões que dependem de cartão do dono

1. Desastre regional: aceitar Multi-AZ (PLT-04, opção a), incluir cofre isolado (b), cópia em segundo provedor brasileiro (c) ou pilot-light (d).
2. Exposição de API pública e de webhooks de saída, e se o primeiro escopo é leitura (PLT-01, PLT-02).
3. Domínio próprio do tenant e, em consequência, login gov.br para tenants de direito público (PLT-05, PLT-06).
4. Níveis do ASVS por componente (PLT-08).
5. Provedor de e-mail da LC.2 (SES com tenants, PLT-10) e política de modelos de WhatsApp.
6. Política de IA e provedor com processamento no Brasil ou transferência internacional (PLT-18, junto de GES-08).
7. Cláusula de notificação do operador ao controlador (PLT-34).
8. Reclassificação da P3.24 depois do levantamento de optantes (PLT-27).

### 5.4 Observações finais

Nenhum achado propõe reescrever decisão aceita. Os ajustes de redação (PLT-08, PLT-26, PLT-27, PLT-31) corrigem critérios de aceite que ficaram desatualizados em relação a fonte normativa ou técnica, e as tarefas novas (PLT-01, PLT-03, PLT-16 e outras) completam capacidades que as fases atuais pressupõem. Esforço, prioridade e encaixe são sugestões e não alteram o plano.
