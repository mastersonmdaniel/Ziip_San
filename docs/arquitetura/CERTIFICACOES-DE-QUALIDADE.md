# Certificações de qualidade: desenho do módulo

Base: ADR-0041 (aceito em 21/10/2026) e levantamentos [21a](../levantamento/certificacoes/21a-iso-gestao-requisitos-de-software.md)
(ISO de gestão) e [21b](../levantamento/certificacoes/21b-normas-do-setor-requisitos-de-software.md) (normas do setor).
Plano v2.17. Este documento resume o desenho; o detalhe por cláusula, por indicador e por lacuna está nos
levantamentos.

## 1. O que o módulo faz

O cliente abre a lista de normas, liga as que deseja demonstrar e declara o escopo e a edição. O sistema já atende o
que é comum a todas as normas; ao ligar uma, passa a exigir os campos, os ciclos e os registros específicos dela e
a mostrar o mapa de cláusulas com a evidência que o próprio sistema reuniu.

**O módulo não certifica.** Certificação é do organismo acreditado, depois de auditoria no cliente. O mapa de
cláusulas é autoavaliação, e a interface o diz em toda tela que o exibe.

## 2. Catálogo

| Classe | Normas de fábrica | Comportamento |
|---|---|---|
| `REGULATORY` | NR 8, 9, 11, 13, 15 e 16 da ANA; NR-1, 18, 33 e 35 do MTE (segurança do trabalho, v2.18); Portaria GM/MS 888/2021 e SISAGUA; CONAMA 357, 430 e 498; Leis 11.445, 14.015, 14.898, 13.460 e 12.007; Decreto 5.440; LGPD; Portaria Inmetro 155/2022 | Obrigatória por aplicabilidade; não desativável; preenchida pelo perfil regulatório |
| `VOLUNTARY` | ISO 9001, 14001, 45001, 55001; ISO/IEC 27001 e 27701; ISO/IEC 17025; ISO 46001; ISO 24510, 24511 e 24512 | Ativável pelo tenant com escopo e edição |
| `REFERENCE` | NBR 12218, 9649, 9648, 12586, 12587, 13133, 14166, 16043 e 15538 | Ativável como checklist de completude |
| `EXPORT_PROFILE` | ISO 37120, PNQS | Perfil de exportação de indicadores, sem entrada própria |

O CONAMA 503/2021 não entra para esgoto sanitário: trata de fertirrigação com efluentes de certas indústrias (21b).
As ISO 24510, 24511 e 24512 não são certificáveis; servem de base do sistema de indicadores. A ISO/IEC 17025 é
acreditável pela Cgcre/Inmetro, não certificável.

## 3. Camadas

1. **Capacidades comuns (sempre disponíveis, 17):** informação documentada; contexto e partes interessadas; política
   e papéis; riscos e oportunidades; objetivos e indicadores; mudança; competência e treinamento; comunicação e
   satisfação; controle operacional; fornecedor; requisitos legais; monitoramento e medição; auditoria interna;
   análise crítica pela direção; não conformidade, ação corretiva e melhoria; incidente; emergência e continuidade.
2. **Específico por norma (só com a norma ligada):** 9001 mapa de processos, calibração, saída não conforme; 14001
   aspectos e impactos, licenças e emergência ambiental; 45001 perigos, análise de risco da tarefa e permissão de
   trabalho na OS, EPI, investigação de acidente; 55001 inventário, ciclo de vida, criticidade, plano estratégico de
   ativos; 27001 declaração de aplicabilidade, matriz de responsabilidade compartilhada Ziip e cliente,
   recertificação de acessos; 27701 registro de operações de tratamento, direitos do titular, incidente com dado
   pessoal, RIPD.
3. **Do setor:** indicadores das ISO 24510 e 24512 e da NR 9; balanço de perdas da NR 15; sistemas de abastecimento e
   de esgoto; relatório anual de qualidade da água; esgoto e água bruta; ISO 17025; hidrômetros pelo regulamento
   metrológico; uso próprio de água (ISO 46001).

## 4. Regras de ativação

- Ligar: `cert.<norma>.enabled`, com `cert.scope_statement` e `cert.<norma>.edition`. Proposta e aceite seguem o
  mecanismo de `parameter_versions` e `parameter_acceptances` (quem propõe não aprova).
- Desligar nunca apaga registro (classe a).
- Troca de edição gera comparação de cláusulas e lista de evidências a revalidar.
- Registro único com vínculo a várias cláusulas (`cert_clause_map`, `cert_evidence_link`).
- A lei vence: se uma norma ativa pedir retenção ou acesso mais restritivo que o limite legal travado, o validador
  avisa e o limite legal prevalece.
- Pacote de evidência sem dado pessoal (classe a). Auditor externo desligado por padrão.

## 5. Parâmetros

As chaves `cert.*` estão em [PARAMETROS-DO-TENANT](PARAMETROS-DO-TENANT.md), seção "Certificações de qualidade".

## 6. Arquitetura e dados

- Motor no núcleo da plataforma; conteúdo de saneamento no pacote `sanitation` (ADR-0037).
- Catálogo global de normas, edições e cláusulas no banco do core (`V9_8`); registros do tenant no schema do tenant
  (`V21_x`): ativação, mapa e vínculo de evidência, documentos, riscos, objetivos, mudanças, treinamentos, avaliações
  de fornecedor, requisitos legais, programa e achados de auditoria, ocorrências e ações, análises críticas.
- Permissões `cert:read`, `cert:write`, `cert:approve`, `cert:audit` (ADR-0040).
- Texto das normas não entra no produto: só título, edição e número de cláusula; o mapa é escrito em palavras
  próprias com base nas normas compradas para a curadoria.

## 7. Entrega no plano (v2.17)

Estrutura no P1 a P3: P1.32 (núcleo), P2.25 (documentos, ocorrência e ação), P3.31 (riscos, objetivos, auditoria,
análise crítica, competência, fornecedor e requisitos legais). Pacotes de norma e extensões do setor na trilha:
ED-69 a ED-82. Tamanhos a estimar; sem ordem de calendário, apenas de dependência.
