# Checklist — Ligar a emissão fiscal em produção (`tpAmb=1`) — LC.13

> **Produtor:** sessão `95ff17bd-dd7b-4269-a848-c235bad7f4be` (papel: Produtor), 09/11/2026.
> Checklist do evento real; nenhum item está marcado como feito. Sem os dois
> registos de aprovação da seção 2, a emissão em produção **não** é ligada.

## 1. Fonte do procedimento de aprovação dupla — estado da fonte

O aceite da LC.13 exige aprovação dupla conforme **FISCAL-PORTE-JAVA §3.6**.
**Verificação executada nesta produção:** o documento `FISCAL-PORTE-JAVA.md`
**não foi localizado nas fontes locais** desta VM (busca por nome e por
conteúdo em `~/workspace/ziipsan-producao/` e `~/workspace/ziipsan-retomada/`:
só existem citações ao documento — ex.: `p3.7/RELATORIO-P3.7.md`, que regista
que ele também não foi lido ao vivo por HTTP 429). Fica portanto declarado:

- **[SEM FONTE] P-LC13-FISCAL-36 — o texto do FISCAL-PORTE-JAVA §3.6 não está
  disponível localmente.** Este checklist implementa a aprovação dupla na
  forma mínima que o próprio aceite enuncia (duas aprovações registadas antes
  de ligar `tpAmb=1`) e **deve ser conferido contra o §3.6** quando o
  documento estiver acessível, antes do evento real. Divergência encontrada
  nessa conferência prevalece sobre este checklist e é registada.

## 2. Aprovação dupla (portão — antes de qualquer emissão em produção)

| # | Aprovação | Quem (papel) | Registo exigido |
|---|---|---|---|
| A1 | Primeira aprovação: conformidade fiscal da virada (parâmetros do tenant, certificado, séries/numeração, ambiente de destino) | Responsável fiscal designado [A DEFINIR no evento] | Nome, cargo, data/hora e assinatura no registo do corte |
| A2 | Segunda aprovação: autorização operacional independente da primeira (quem aprova não executou a configuração) | Segundo aprovador designado [A DEFINIR no evento] | Nome, cargo, data/hora e assinatura no registo do corte |

Regras: A1 e A2 são pessoas distintas; nenhuma das duas é quem executa o
passo de virada; aprovação verbal não conta — só registada.

## 3. Verificações antes de ligar (executor; cada item com evidência)

- [ ] Homologação fiscal concluída no desenho da P3.7 (`tpAmb=2`), com o
      roteiro de homologação executado e registado — referência: ____
- [ ] Certificado de produção do cliente presente no cofre (LC.4) e válido;
      nenhum certificado de teste/homologação no caminho de produção — evidência: ____
- [ ] Parâmetro de ambiente fiscal do tenant a apontar para produção
      (`tpAmb=1`) identificado e fotografado **antes** da alteração — evidência: ____
- [ ] Numeração/séries de produção conferidas contra o legado congelado
      (roteiro LC.13, passo 2) para não haver sobreposição — evidência: ____
- [ ] Aprovações A1 e A2 registadas (seção 2) — referências: ____

## 4. Virada e primeira emissão

- [ ] Alteração para `tpAmb=1` executada por: ____ em: ____ (instante)
- [ ] Primeira emissão de produção executada e o seu retorno registado
      (autorizada/rejeitada, protocolo quando houver) — referência: ____
- [ ] Documento de produção conferido: ambiente = produção, dados do
      emitente/destinatário, valores — conferido por: ____
- [ ] Se a primeira emissão for rejeitada de forma sistémica: acionar a
      volta do roteiro LC.13 §5 e registar — não insistir em produção.

## 5. Depois de ligada

- [ ] Emissão em homologação (`tpAmb=2`) do tenant de produção encerrada ou
      segregada, para não haver emissão paralela ambígua — registo: ____
- [ ] Resultado comunicado ao cliente e registado no Termo de Decisão de
      Corte (LC.13) — referência: ____
