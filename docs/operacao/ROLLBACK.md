# Procedimento de rollback da IaC (LC.2)

> Pacote LC.2 — Produtor, sessão `764b25ad-2ccf-40e5-afbf-986d42be0213`.
> Correção (estágio 3), sessão `b36fd7a8-91ec-4322-9e7e-8bfe051efd54`:
> passos reescritos em alinhamento com o workflow, que passou a partir
> o rollback em `rollback-plan` (sem gate) e `rollback` (apply, com o
> Environment) — a aprovação de produção acontece **depois** de o plano
> existir e poder ser conferido (menor da revisão). Ver
> `CORRECAO-LC.2.md`.
> **Estado honesto:** este é o PROCEDIMENTO. O **exercício** do rollback
> (item do aceite da LC.2) exige a AWS aplicada e **não foi executado**
> neste bloco — pendência externa **P-LC2-02**. Nenhuma linha deste
> documento afirma que um rollback já rodou.

## Princípio

Rollback de infraestrutura = reaplicar, **pelo mesmo pipeline**, a última
revisão da IaC conhecida boa (ref de commit/tag no GitHub), gerando um
plano novo contra o state corrente — nunca editar o state à mão, nunca
`destroy` como atalho, nunca aplicar por fora do pipeline. Rollback de
**aplicação** (imagem anterior no ECS) é complementar e segue o mesmo
princípio: a release anterior é reaplicada pela variável `container_image`
(digest anterior) num commit de reversão, não por comando manual no console.

## Pré-condições

1. Ref alvo identificada (commit/tag da última revisão boa) e registrada
   no chamado/incidente que motivou o rollback.
2. State íntegro: o job `validate` do pipeline passou e o lock do backend
   está livre (nenhum apply concorrente — o grupo de concorrência
   `iac-<ambiente>` do workflow garante serialização).
3. Para **produção**: aprovação manual no Environment `producao`, mesma
   exigência do apply normal — pedida no job de apply do rollback,
   **depois** de o plano ter sido gerado pelo job `rollback-plan`
   (passos abaixo). A ordem é esta de propósito: ninguém aprova um
   rollback de produção sem o plano existir para ser conferido.

## Passos (pipeline)

1. Disparar `deploy-infra-lc2` (`workflow_dispatch`) com
   `action = rollback`, `environment = <ambiente>` e
   `rollback_ref = <ref boa>`.
2. O job `rollback-plan` (sem Environment, sem aprovação) faz checkout
   da ref, assume o papel do ambiente — **em produção, o papel de
   produção** (`AWS_IAC_ROLE_ARN_PROD`), como no apply normal —, `init`
   com o `backend.hcl` do ambiente e `plan -out=tfplan-rollback`,
   guardando o plano como artefato da execução
   (`tfplan-rollback-<ambiente>`).
3. Conferir o plano gerado, **antes** de aprovar produção: o diff deve
   conter apenas o retorno esperado (recursos criados/alterados pela
   revisão defeituosa). Diff fora do esperado = parar e tratar como
   incidente, não como rollback (não aprovar o passo seguinte).
4. O job `rollback` (apply) só então pede a aprovação: em **produção**,
   fica retido no Environment `producao` até um revisor requerido
   aprovar; aprovado, descarrega o artefato do plano conferido
   (`download-artifact`) e faz `apply -auto-approve tfplan-rollback`
   com o papel do ambiente (em produção, `AWS_IAC_ROLE_ARN_PROD`).
   Rejeitar a aprovação = nenhum apply acontece.
5. Pós-apply: smoke mínimo do ambiente (saúde do ALB, distribuição da
   borda respondendo em HTTPS com HSTS, eventos SES fluindo para o SNS do
   tenant de teste) e registro do resultado no incidente.

## O que o rollback NÃO faz

- Não reverte **dados**: migration de banco já aplicada, massa semeada e
  mensagens já enviadas ficam fora — reversão de dado é procedimento da
  LC.3 (PITR) e do pacote de reversão da ED-04, não desta IaC.
- Não apaga recursos de segurança (GuardDuty/Inspector/Config): se a
  revisão defeituosa os tiver criado, o plano de rollback os mantém;
  removê-los é decisão separada e registrada.

## Exercício pendente (como será provado)

Quando houver conta AWS (P-LC2-01): aplicar homologação, introduzir uma
mudança reversível marcada (ex.: alterar `api_desired_count` em commit
próprio), executar este procedimento de volta à ref anterior e registrar
tempo, plano e resultado em `docs/operacao/EXERCICIO-ROLLBACK-<data>.md`.
Só esse registro fecha o item "rollback exercitado" do aceite.
