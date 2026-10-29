## 8. Governança

- **Release.** `main` protegida; PR com CI verde; versão semântica gerada da tag (ADR-0021), com notas de versão e item
  obrigatório no changelog do cliente para segurança, legislação e parâmetro regulatório; homologação antes de
  produção, com aprovação manual; `migrate` antes do rollout; rollback da imagem testado; janela de manutenção avisada
  com a antecedência do SLA.
- **Ambientes (ADR-0033).** Até o lançamento comercial: desenvolvimento local (sintético), CI (banco efêmero),
  homologação local e demonstração local (sintético ou anonimizado na origem), todos na máquina de desenvolvimento, sem
  nenhum dado pessoal real e com segredos só no cofre local. A partir do lançamento: homologação e demonstração na AWS
  (sintético) e produção, a única com dado real, com KMS e Secrets Manager obrigatórios. Segredo do ambiente local
  nunca é reaproveitado na AWS.
- **API.** `/api/v1`; OpenAPI no repositório, conferido no CI; mudança aditiva na mesma versão; incompatível vai para
  `/api/v2`, com convivência mínima de seis meses.
- **Feature flags por tenant.** No console (core `V9`); módulo contratado é flag de plano; flag de rollout tem dono e
  data de remoção; mudança vai para a auditoria.
- **ADRs.** Regras em `arquitetura/adr/README.md`; decisão nova só por ADR, que este plano cita sem repetir.
- **Idioma (decisões do dono do produto, 12/10/2026).** Tabelas, colunas, schemas, valores de enumeração gravados,
  rotas da API e do frontend, cabeçalhos HTTP, propriedades, permissões, chaves de configuração e de arquivos de
  conteúdo, pastas e identificadores de código em inglês, como o código atual (`consumer_units`, `'ACTIVE'`,
  `/api/registry/consumer-units`); textos exibidos ao usuário, conteúdo de ajuda, mensagens e documentação em português. Rotas
  novas: `/help`, `/support`, `/about`; `/api/v1/public/status` se mantém. Os códigos de contrato da API também ficam
  em inglês: prefixo `urn:ziipsan:problem:` e códigos de problema em minúsculas com hífen (`stale-version`,
  `mfa-required`), aplicados na P0.33 (tabela no P0) e congelados depois dela (MARCA-E-TEMAS §5.5); `title` e
  `detail` do problem+json continuam em português. Única exceção: o formato do protocolo exibido ao usuário
  (`SUP-AAAA-NNNNNN`). Nomes definidos fora do produto, como campos de leiautes oficiais (`tpAmb`, `cStat`) e código
  de terceiros citado (C# do Ziip Cloud), ficam como na fonte. Em revisão de PR e de documento, nome novo em
  português é corrigido antes do merge.
- **Diário.** `docs/DIARIO-DE-BORDO.md` ao fim de cada tarefa: o que foi feito, testes, pendências, commit.
- **Push automático (ADR-0009).** Fim de etapa com suíte verde → commit e push; vermelho não publica.
- **Ordem.** Uma fase aberta por vez; tarefa bloqueada não libera antecipação de funcionalidade de fase posterior;
  revisão de escopo ao fim de cada fase; o P4 cede antes da homologação local do piloto.
- **Aceite de fase.** Todos os critérios cumpridos e registrados; tarefa que escorregar recebe nova data aqui e no diário.
  A partir do P1, todo fim de fase tem ensaio interno de POC com resultado registrado; a partir do P3, a meta de ensaio
  da fase entra no aceite (P3: P3.23; P4: fim das ondas A e B da trilha de editais), conforme o ADR-0023.
- **Matriz de editais.** A matriz viva (P1.25; consolidado 18, §7) é atualizada ao fim de cada tarefa que toca
  requisito de edital, a cada ensaio e a cada edital novo; teste que prova requisito leva o ID do requisito. Edital
  novo segue a regra do consolidado 18, §7.3 (extração, equivalência, matriz por área, conflitos legais, decisão de
  disputar).
