# P3.40 — Privacidade do titular (todo tenant)

Direitos do titular, registro de operações de tratamento e canal no portal, sobre o que
o módulo `lgpd` já modela (`lgpd_data_subject_request`, `lgpd_consent`, `lgpd_dpia`,
`lgpd_retention_schedule` — `V2_5`/`V2_7`, ampliadas pela `V23_1`). Obrigação de todo
tenant: não depende de pacote (a ED-74 é o que a ISO/IEC 27701 pede além da lei) nem de
flag — guarda em `PrivacyAvailability` e no teste dela.

## Scan da aplicação

O módulo `lgpd` entra no scan da aplicação: os controllers
`privacy/web/PrivacyPortalController` e `privacy/web/DpoBackofficeController` e os
serviços do pacote `br.com.ziipsan.compliance.lgpd` precisam estar no component scan da
aplicação (e o `PrivacyFacade` precisa de uma implementação ligada). **Pendência de
integração nomeada:** a classe principal da aplicação não foi lida nesta frente (a
leitura do repo parou em HTTP 429 antes dela); na integração, conferir a anotação de
scan da aplicação e, se ela enumerar pacotes, acrescentar
`br.com.ziipsan.compliance.lgpd` — sem isso as rotas abaixo não existem, e é exatamente
o sintoma que o aceite manda corrigir ("o módulo lgpd existente entra no scan").

## Canal do titular (portal, área Privacidade)

Rotas de `PrivacyPortalController` (`/portal/privacidade`), titular autenticado da P3.3:

| Rota | O quê |
|---|---|
| `GET /encarregado` | Encarregado do tenant da fonte única `lgpd.dpo_contact` (Res. CD/ANPD 18/2024) |
| `GET /politica` | Política de privacidade publicada e versionada do tenant |
| `POST /pedidos` | Abre pedido (direito do art. 18); nasce como protocolo da P1.36 do tipo privacidade, prazo de 15 dias (art. 19, II) |
| `POST /consentimentos/{id}/revogar` | Revogação do próprio consentimento, finalidade pelo catálogo da P1.3 |
| `POST /pedidos/{id}/entrega` | Entrega da resposta/arquivo (acesso ou portabilidade) — só após identidade verificada, com registro no `personal_data_access_log` |

No atendimento, o atendente abre o mesmo pedido pelo protocolo (canais `COUNTER` /
`PHONE`); pedido por representante usa a representação da P1.41 (poder `REQUEST`,
vigência) e o acesso delegado é registrado no log.

## Backoffice do encarregado (menu Privacidade)

Item de menu **Privacidade**, visível ao perfil de fábrica de encarregado de dados da
P1.37 (a ligação perfil → menu é da camada de autorização; a permissão exata do
catálogo da P0.13 é pendência de integração nomeada no relatório). Rotas de
`DpoBackofficeController` (`/privacidade`): `GET /relatorio` (pedidos por tipo,
situação e prazo; consentimentos por finalidade; operações; RIPD; alertas de acesso
anômalo da P0.11/ADR-0008/RNF-41), `GET /pendencias-cobertura` (coluna classificada sem
operação de tratamento associada) e `GET /operacoes` (registro do art. 37 com versões).
A fila de trabalho do encarregado é a caixa **Minhas tarefas** da P2.28, tipo
`HOLDER_REQUEST`, com responsável e prazo — esta tarefa não cria uma segunda caixa.

## Exibição do encarregado (teste em cada lugar)

O mesmo `DpoContact` (nome + contato público de `lgpd.dpo_contact`) é renderizado por
`DpoDisplay.render` nos três lugares — portal (`Place.PORTAL`), política de privacidade
(`Place.PRIVACY_POLICY`) e documentos (`Place.DOCUMENT`) — e `DpoDisplayAndPolicyTest`
confere nome e contato nos três. Sem o parâmetro registrado, a exibição é recusada com
erro explícito: encarregado não se inventa.

## Registro de operações e pré-carga de fábrica

Estrutura na `V23_1`; a repetível
`R__lgpd_factory_processing_records.sql` está reservada para a carga de fábrica,
**sem nenhum SQL de carga e sem linhas nesta entrega**: nenhuma fonte
enumera as operações de fábrica do pacote `sanitation` nem semeia finalidades no
catálogo `consent_purposes`. O relatório do encarregado expõe
`processingFactoryRecordsLoaded` para a pendência ficar visível até o manifesto da
vertical (P0.47) ser lido e a carga entrar na repetível.
