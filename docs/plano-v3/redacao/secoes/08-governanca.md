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
  critério de remoção; mudança vai para a auditoria.
- **ADRs.** Regras em `arquitetura/adr/README.md`; decisão nova só por ADR, que este plano cita sem repetir. Linha de
  ajuste num ADR só para esclarecimento, sem mudança de substância; mudança de substância por ADR novo que substitui o
  anterior (regra 2, PD-03). Decisões do dono do produto não se alteram sem cartão.
- **Datas (ADR-0044).** Datas de fase, marco e lançamento são datas de referência; o plano não usa "prazo", "atraso",
  "folga" nem "data-limite" para o próprio projeto. Pendência do dono se descreve pelo que falta decidir e pela tarefa
  que precisa da decisão ("Antes de"). Datas de norma são fatos e ficam registradas como tais.
- **Tamanho e estimativa.** P, M, G e semanas-pessoa indicam esforço, sem conversão em equipe nem em calendário
  (ADR-0035); tarefa nova fica "a estimar" até o dono decidir reestimar.
- **Tarefa dona.** Cada achado tem uma tarefa dona, que constrói e traz o critério testável, ou um destino documental
  (mapa de donos da v3.0). Tarefa que usa o que outra constrói escreve "usa X da tarefa Y" e tem a dona no fecho de
  dependências; ponto que pertence a outra tarefa é remetido ("ver P1.36"), sem repetir o critério.
- **Dependências.** Toda dependência vai na coluna "Dep."; dependência textual só quando a própria tarefa a escreve por
  extenso (por exemplo, "todas as fases e ondas, exceto os pacotes pendentes" na LC.1). Nenhuma tarefa depende de fase
  ou onda posterior, nem da seção de pacotes pendentes.
- **Regras transversais de aceite.** Valem para toda tarefa: permissão nova entra nos perfis de fábrica pertinentes, e o
  CI recusa permissão fora de perfil (P1.37); evento publicado fora do catálogo único é recusado no CI (P1.38); tarefa
  que cria uma fonte da ficha da unidade consumidora acrescenta a aba correspondente (P0.47); solicitação externa nasce
  como protocolo de atendimento e gera OS, e alteração interna gera OS sem protocolo (ADR-0039, item 7); bifurcação
  normativa vira parâmetro com padrão mais protetivo e o que lei federal proíbe fica travado (ADR-0038).
- **Idioma (decisões do dono do produto, 12/10/2026).** Tabelas, colunas, schemas, valores de enumeração gravados,
  rotas da API e do frontend, cabeçalhos HTTP, propriedades, permissões, chaves de configuração e de arquivos de
  conteúdo, pastas e identificadores de código em inglês, como o código atual (`consumer_units`, `'ACTIVE'`,
  `/api/registry/consumer-units`); textos exibidos ao usuário, conteúdo de ajuda, mensagens e documentação em português.
  Rotas novas: `/help`, `/support`, `/about`; `/api/v1/public/status` se mantém. Os códigos de contrato da API também
  ficam em inglês: prefixo `urn:ziipsan:problem:` e códigos de problema em minúsculas com hífen (`stale-version`,
  `mfa-required`), aplicados na P0.33 (tabela no §4, P0) e congelados depois dela (MARCA-E-TEMAS §5.5); `title` e
  `detail` do problem+json continuam em português. Única exceção: o formato do chamado de suporte exibido ao usuário
  (`SUP-AAAA-NNNNNN`). Nomes definidos fora do produto, como campos de leiautes oficiais (`tpAmb`, `cStat`) e código de
  terceiros citado (C# do Ziip Cloud), ficam como na fonte. Em revisão de PR e de documento, nome novo em português é
  corrigido antes do merge.
- **Diário.** `docs/DIARIO-DE-BORDO.md` ao fim de cada tarefa: o que foi feito, testes, pendências, commit.
- **Push automático (ADR-0009).** Fim de etapa com suíte verde → commit e push; vermelho não publica.
- **Ordem.** Uma fase aberta por vez; tarefa bloqueada não libera antecipação de funcionalidade de fase posterior;
  revisão de escopo ao fim de cada fase; o P4 cede antes da homologação local do piloto. Pacote pendente só entra por
  cartão do dono ou por demanda registrada.
- **Aceite de fase.** Todos os critérios cumpridos e registrados no diário, com o gate da fase (§4) atendido. Tarefa
  que não fecha continua aberta, com o motivo registrado no diário, e não recebe data nova. A partir do P1, todo fim de
  fase tem ensaio interno de POC com resultado registrado; a partir do P3, a meta de ensaio da fase entra no aceite (P3:
  P3.23; trilha: fim da onda A e fim do grupo B1), conforme o ADR-0023.
- **Matriz de editais.** A matriz viva (P1.25; consolidado 18, §7) é atualizada ao fim de cada tarefa que toca
  requisito de edital, a cada ensaio e a cada edital novo; teste que prova requisito leva o ID do requisito. Edital
  novo segue a regra do consolidado 18, §7.3 (extração, equivalência, matriz por área, conflitos legais, decisão de
  disputar).

### Glossário

Termos com sentido fixo neste plano e nos documentos que ele cita (COE-17, COE-23). Nomes de tabela entre crases.

| Termo | Sentido no plano |
|---|---|
| Unidade consumidora | Raiz do domínio comercial e operacional (`consumer_units`, ADR-0039): a unidade que recebe o serviço e a fatura. Equivale à "unidade usuária" da NR 11/2024 (art. 3º, XIII) e ao "imóvel" do GSAN e dos textos anteriores à v3.0. Ligações, hidrômetros, leituras, contas, débitos, pagamentos, protocolos, OS e documentos referenciam a unidade consumidora |
| Imóvel | O imóvel físico, o lote: terreno com ou sem edificação, onde ficam uma ou mais unidades consumidoras. Não é sinônimo de unidade consumidora. O piso de agregação do GIS ("no mínimo 5 imóveis") conta imóveis neste sentido |
| Edificação | Construção sobre o imóvel; como alvo de OS (`BUILDING`), edificação ou estrutura civil sem rede do próprio prestador |
| Economia | Subdivisão da unidade consumidora para tarifa e consumo (uma economia por família ou atividade), por categoria e subcategoria, com vigência (`unit_subcategories`) |
| Ligação | Conexão física de água ou de esgoto entre a rede e o imóvel (`service_connections`): uma ativa por tipo e por imóvel, com uma ou mais unidades consumidoras atendidas, como no condomínio (P2.23). O ramal é o trecho de rede da ligação |
| Matrícula | Identificador da unidade consumidora no tenant (`registration_number` com `check_digit`), gerado por sequência (P1.4). Não é entidade: "saldo da matrícula" lê-se "saldo da unidade consumidora" |
| Cliente | Pessoa física ou jurídica ligada à unidade consumidora por vínculo com papel e vigência (proprietário, usuário titular da fatura, responsável e outros). A fatura sai em nome do usuário titular |
| OS (ordem de serviço) | Registro de toda alteração de cliente ou de unidade consumidora e de todo serviço, de campo ou interno, com um ou mais alvos tipados e efeito aplicado no encerramento (ADR-0039, ADR-0045). OS de lote aplica a mesma operação a vários alvos; OS inversa desfaz o efeito de outra |
| Protocolo de atendimento | Registro de toda solicitação externa, por qualquer canal, com número, canal, solicitante, motivo e prazo (NR 11, art. 52; P1.36). Vem sempre antes da OS que ele gera. Não se confunde com o chamado de suporte |
| Chamado de suporte | Pedido do tenant ao suporte do Ziip, com número `SUP-AAAA-NNNNNN` (ADR-0020, P1.23) |
| Tenant | Cliente do SaaS (prestador de serviço de saneamento: autarquia, órgão, companhia ou empresa privada), com dados isolados por schema ou por linha (ADR-0005), parâmetros, marca e pacotes de vertical próprios |
| Perfil regulatório | Conjunto de valores de parâmetros do tenant que reproduz as regras de uma agência reguladora (prazos, avisos, encargos, média, vencimento), cada valor com ato, artigo e vigência; pré-carregado para as agências de SC (P2.31) e ativado pelo tenant com simulação prévia |
| Perfil de acesso | Conjunto de permissões `recurso:ação` com menu e página inicial; os perfis de fábrica (atendente, faturista, leiturista e outros) são do produto (P1.37), e o tenant compõe os seus na ED-06 |
| Onda | Grupo de entregas da trilha de editais (A, B e C), ordenado pela dependência e pelo peso nos editais; a onda B tem os grupos B1 e B2 |
| Pacote pendente | Tarefa ou entrega parada fora das ondas, dos totais de esforço e do critério do lançamento, que só entra por cartão do dono ou por demanda registrada (P4.6, ED-68, ED-102, ED-103, ED-104) |
| Pacote (outros sentidos) | Sempre qualificado: pacote de vertical (ADR-0037), pacote de edição do GIS (ADR-0034), pacote de campo (offline), pacote de reversão (ADR-0024), pacote por norma (Certificações de qualidade), pacote de evidência, pacote de marca |
| Central Ziip | Só o console da plataforma (ADR-0018). As demais telas usam o próprio nome: "central de avisos" (P3.36), "documentos fiscais" (P2.33) |
| Homologação local e primeira produção | Homologação local do piloto é a fase GL, no ambiente local, sem dado real; primeira produção é o corte do legado no lançamento (LC.13). "Go-live" só aparece no histórico |
| Data de referência | Data que indica sequência e ordem de grandeza de uma fase ou marco, sem valor de compromisso (ADR-0044) |

---
