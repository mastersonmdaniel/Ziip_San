# Capacitação da TI do cliente — ZiipSan (ED-11)

A capacitação da TI é o módulo `TI_CLIENTE` do catálogo (40 h, mesmas
cinco fases do plano) com conteúdo próprio: os tópicos que a equipe de
TI do tenant precisa dominar para operar o ZiipSan sem depender da
Ziip no dia a dia. A régua de conteúdo está em `ItEnablement` — tópico
obrigatório não entregue, ou entregue sem a prática quando ele a
exige, deixa a capacitação incompleta e o gate do treinamento aberto.

## Tópicos

| Tópico (chave) | Conteúdo | Origem | Prática no pré-treinamento |
|---|---|---|---|
| `IDENTIDADE_E_SSO` | Keycloak em produção: organizações, MFA do tenant e SSO (OIDC, SAML, LDAP) | P0.29, P0.15, ED-06 | sim |
| `USUARIOS_E_PERFIS` | Gestão de usuários, catálogo de permissões `recurso:ação` e perfis de fábrica e próprios | P0.13, P1.37, ED-06 | sim |
| `PARAMETROS_DO_TENANT` | Parâmetros: propor, aprovar e aceitar, vigência futura, pacote de parâmetros aceitos entre homologação e produção | P0.19, P1.6, ED-09 | sim |
| `INTEGRACOES` | API pública, webhooks de saída e hub de integrações: credenciais, saúde, reprocessamento e teste de contrato | P2.26, P3.37 | sim |
| `BACKUP_E_RESTAURACAO` | Backup e PITR, restauração ensaiada da implantação e restauração seletiva de um tenant | P0.28, ED-09, P3.33 | sim |
| `OPERACAO_E_OBSERVABILIDADE` | Runbook de operação, plantão, página pública de status e error tracking | P3.11, P3.18 | não |
| `ACESSO_ASSISTIDO_E_EMERGENCIA` | Acesso assistido com consentimento e acesso de emergência: limites, registro e auditoria | P3.17, P3.8 | não |
| `CERTIFICADO_FISCAL` | Certificado fiscal no cofre do tenant e homologação fiscal a partir do ambiente local | P1.8, P3.7 | não |
| `INSTANCIA_DEDICADA` *(condicional)* | Instância dedicada, quando o contrato a exige: acesso administrativo do órgão somente leitura, com MFA e auditoria | LC.15, ED-04, ADR-0024 | não |

## Regras da capacitação

1. **Prática é parte do tópico.** Nos tópicos com prática, a TI executa
   a operação no tenant de pré-treinamento — configurar o SSO, compor
   um perfil, aceitar um parâmetro, reprocessar uma integração e
   restaurar um backup de ensaio. Assistir à demonstração não completa
   o tópico.
2. **Nada de acesso além do desenho.** A capacitação não concede papel
   de plataforma (P0.43 é da Ziip) nem acesso administrativo à
   infraestrutura compartilhada: o acesso administrativo do órgão,
   somente leitura, existe só na instância dedicada (LC.15) e é o que
   o tópico condicional ensina a usar.
3. **Sem impersonação.** Suporte ao usuário é pelo acesso assistido com
   consentimento (P3.17, ADR-0020) — a TI aprende esse fluxo, não um
   atalho por cima dele.
4. **A TI sai sabendo onde fica a evidência:** trilha de auditoria das
   ações (P1.7/`platform.audit_event`), registro das leituras de dado
   pessoal (P0.11) e a página de status (P3.18) são parte dos tópicos
   de operação e de acesso, não material opcional.

## Conclusão

A capacitação da TI conclui em duas camadas, e as duas são exigidas
pelo gate (`TrainingGate`):

- **conteúdo** — todos os tópicos obrigatórios entregues com as suas
  práticas (`ItEnablement`), mais o tópico condicional quando o
  contrato do tenant exige instância dedicada;
- **entrega do módulo** — as 40 h do módulo `TI_CLIENTE` cumpridas
  pela régua comum (`TrainingCompletion`): unidades, horas e
  participantes (a equipe de TI indicada pelo cliente) concluídos.

Quem compõe a equipe de TI treinada é o cliente:
**[A DEFINIR COM O CLIENTE: membros da TI do tenant que cursam a
capacitação, por turma]**.
