# LC.13 — Roteiro de corte do legado e volta (primeira produção)

> **Produtor:** sessão `95ff17bd-dd7b-4269-a848-c235bad7f4be` (papel: Produtor).
> **Data de produção:** 09/11/2026. **Natureza:** roteiro para o evento real futuro.
> **Nenhum corte foi executado.** Não há produção AWS, legado de piloto nem
> cliente (DECISOES-DO-DONO.md, item 34: venda por licitação após a conclusão).
> Nenhuma data, nome ou resultado é afirmado — os factos do evento registam-se
> no Termo de Decisão e nos checklists deste pacote, em branco.

## 0. Aceite que este roteiro cumpre (fonte única, verbatim)

Fonte: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
registro LC.13 (título "Corte do legado: primeira produção", origem ADR-0001,
tamanho P, deps LC.11, LC.12 e P3.24):

> "decisão formal de corte registrada; emissão fiscal ligada (`tpAmb=1`) com
> aprovação dupla (FISCAL-PORTE-JAVA §3.6); com optantes no piloto (P3.24,
> condicional), débito automático em produção pela P3.24; sem optantes, o
> registro do corte informa que a P3.24 não se aplica; legado do piloto
> desligado (M15)"

Marco: **M15 — primeira produção** (marcos do ADR-0033, conforme fontes locais).

## 1. Relação com o plano preliminar da P3.13 (referência, sem presumir conteúdo)

O plano preliminar do paralelo real, do corte e da volta é peça da **P3.13**
("Plano da homologação local e do paralelo real"). Estado verificado nesta
produção (09/11/2026): em `~/workspace/ziipsan-producao/p3.13/` existe apenas
`RELATORIO-VERIFICACAO-P3.13.md` (verificação de dependências); a entrega da
P3.13 é produzida **por outra frente, neste mesmo bloco**. Este roteiro:

- **referencia** o plano preliminar da P3.13 como documento-mestre do corte
  (caminho esperado: pacote `p3.13/entrega/`, destino em `docs/` [A DEFINIR]
  pela frente da P3.13);
- **não presume** o seu conteúdo: onde o plano preliminar fixar critérios
  (janela, critério de divergência do paralelo LC.11, condições de volta),
  este roteiro manda lê-los dele no evento e registar a referência no Termo;
- em caso de divergência entre este roteiro e o plano aprovado pelo piloto,
  vale o plano aprovado — divergência é registada, nunca resolvida em silêncio.

## 2. Pré-condições (todas verificadas e registadas no Termo antes do passo 1)

| # | Pré-condição | Estado hoje |
|---|---|---|
| PC-1 | LC.11 concluída: paralelo com dado real dentro do critério, divergências explicadas | **PENDÊNCIA EXTERNA** — sem cliente/dado real |
| PC-2 | LC.12 concluída e apta (Folha de Registo assinada, dentro do RTO ou com decisão expressa sobre o fora-do-RTO) | **PENDÊNCIA EXTERNA** — sem RDS/produção (ver pacote `lc.12/`) |
| PC-3 | Contrato assinado com o piloto/cliente (LC.9: SaaS, DPA, acordo de tratamento) | **PENDÊNCIA EXTERNA** — sem cliente (item 34) |
| PC-4 | Decisão formal de corte tomada e registada (Termo deste pacote, preenchido e assinado no evento) | Ato do dono do produto + cliente — **não** pré-preenchido |
| PC-5 | Ramo da condicional P3.24 apurado e registado (seção 4): há ou não optantes no piloto | Apuramento é facto do piloto real — [A DEFINIR] no evento |
| PC-6 | Janela de corte e janela limite de volta combinadas com o cliente | [A DEFINIR] no evento, a partir do plano da P3.13 |

## 3. Sequência do corte (ordem obrigatória)

1. **Abertura.** Registar abertura da janela, papéis presentes e PC-1…PC-6.
   Falhando qualquer pré-condição: não há corte; regista-se o impedimento.
2. **Congelar o legado.** Última leitura/emissão no sistema legado; instante
   registado. A partir daqui o legado não emite nem altera — só consulta.
3. **Emissão fiscal em produção.** Ligar a emissão fiscal com `tpAmb=1`
   executando o checklist próprio deste pacote
   (`CHECKLIST-EMISSAO-FISCAL-TPAMB1-LC13.md`), que inclui a **aprovação
   dupla** antes da primeira emissão. Sem as duas aprovações registadas,
   `tpAmb=1` não é ligado.
4. **Débito automático — ramo aplicável (seção 4).** Executar o ramo A ou
   registar o ramo B no Termo. Nenhum débito é disparado fora do ramo A.
5. **Primeira produção.** Primeiro ciclo real no ZiipSan (leitura →
   faturamento → emissão) com acompanhamento registado passo a passo;
   qualquer falha aqui dentro da janela de volta aciona a seção 5.
6. **Desligamento do legado (M15).** Ato **do cliente** (seção 6), depois da
   primeira produção confirmada.
7. **Encerramento.** Termo completado com os registos dos passos 2–6,
   desvios e assinaturas; o marco M15 só se declara com o Termo completo.

## 4. Os dois ramos da condicional P3.24 (débito automático)

Decisão de fundo já tomada pelo dono (DECISOES-DO-DONO.md, **item 9**,
08/11/2026): **caminho duplo** — campanha de conversão ao Pix Automático
(P4.5) **e** FEBRABAN 150 mantido como legado para a base atual de optantes;
**débito novo só por Pix Automático**. O parecer jurídico formal RAS-06/PD-10
continua inexistente como documento — a decisão é do dono, não é parecer
(declaração expressa do pacote `p3.24/`, lida nesta produção). A P3.24 está
entregue e aprovada localmente (FEBRABAN 150 para autorizações legadas).

- **Ramo A — COM optantes no piloto:** o débito automático entra em produção
  **pela P3.24**: autorizações legadas importadas e conferidas por matrícula,
  remessa/retorno FEBRABAN 150 em produção, só para optantes com banco do
  pagador = banco do recebedor; em paralelo corre a campanha de conversão ao
  Pix Automático (P4.5) com consentimento guardado com prova. Evidência:
  registo da primeira remessa e do primeiro retorno no Termo.
- **Ramo B — SEM optantes no piloto:** a P3.24 **não se aplica**. O Termo de
  Decisão regista expressamente "P3.24 não se aplica — piloto sem optantes",
  e nenhum passo de débito legado é executado. Autorizações novas, se
  surgirem, são só por Pix Automático (P4.5), conforme o item 9.

O ramo é apurado sobre o levantamento de optantes do piloto real (PC-5);
nesta produção nenhum ramo é dado como ocorrido.

## 5. Volta (rollback do corte) — dentro da janela limite (PC-6)

Acionada por: falha na primeira produção (passo 5), emissão fiscal rejeitada
de forma sistémica, ou decisão registada do cliente/dono do produto.

1. Desligar a emissão em produção (`tpAmb=1` → estado anterior ao corte),
   registando instante e motivo; documentos já emitidos em produção **não**
   são apagados — o seu tratamento segue a regra fiscal aplicável e fica
   registado (cancelamento/inutilização quando cabível, nunca edição).
2. Se o Ramo A correu: suspender novas remessas P3.24; débitos já remetidos
   seguem o retorno normal — a volta não desfaz facto bancário consumado.
3. Descongelar o legado: o sistema legado volta a ser o oficial e emissor,
   registando o instante; dados produzidos no ZiipSan durante a tentativa
   são preservados para reconciliação posterior (nada é descartado).
4. Registar a volta no Termo (motivo, instantes, responsáveis) e o novo
   estado: corte **não** consumado; M15 não declarado.
5. Fora da janela limite, "volta" já não é este procedimento: é decisão nova
   formal (dono + cliente), registada como tal.

## 6. Desligamento do legado (M15) — ato do cliente, declarado

O desligamento do sistema legado do piloto é **ato do cliente/piloto**
sobre o sistema dele: este roteiro não o executa nem o simula. O ZiipSan
regista no Termo a declaração do cliente (quem desligou, quando, sobre que
sistema/instância) depois da primeira produção confirmada (passo 6). Sem
essa declaração registada, o M15 não se declara cumprido.
