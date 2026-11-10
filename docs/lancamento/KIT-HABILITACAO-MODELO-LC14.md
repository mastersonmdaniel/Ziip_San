# Kit de habilitação (M16) — MODELO / índice atualizável — LC.14

> **Produtor:** sessão `95ff17bd-dd7b-4269-a848-c235bad7f4be` (papel: Produtor), 09/11/2026.
> Isto é um **modelo de estrutura**: o índice do kit com o estado de cada
> peça à data desta produção, para ser atualizado e completado no uso real
> (primeira licitação / primeiro atestado). **Não é um kit preenchido** —
> não há cliente, contrato nem certidão real hoje (item 34 do
> DECISOES-DO-DONO.md). Peça sem fonte local está marcada **[SEM FONTE]**;
> peça que só existe no futuro está marcada com a sua pendência nomeada.
> Atualizar = reverificar cada linha na data de uso e registar a data da
> reverificação; linha não reverificada não vai para a proposta.

## Índice do kit

| # | Peça | Para que serve | Estado à data (09/11/2026) | Fonte / pendência |
|---|---|---|---|---|
| K1 | Atestado de capacidade técnica do primeiro cliente (LC.14) | Prova de operação real em produção | **INEXISTENTE — pendência externa:** nasce do evento LC.14 (cliente futuro) | Checklist deste pacote (C6) |
| K2 | Relatório de envio e-Sfinge (ED-33), se autarquia de SC | Prova de remessa ao TCE/SC utilizável no atestado (uso VISAN citado na spec da ED-33) | Motor e relatório entregues localmente; leiaute oficial pendente | Pacote `ed-33/`; pendência itens 29/30 (leiaute TCE/SC) |
| K3 | Contrato SaaS + DPA + acordo de tratamento (minutas P3.9 → contrato LC.9) | Base contratual de referência | Minutas aprovadas localmente (6 minutas, marcadores [A DEFINIR]); contrato assinado inexiste (sem cliente) | Pacote `P3.9/`; LC.9 futura |
| K4 | Relatórios do paralelo e do aceite (GL.2/GL.4, LC.11) | Evidência de equivalência com o legado | Inexistentes como factos (sem piloto); dossiês/roteiros produzidos nos pacotes próprios | Pacotes `gl.2/`, GL.4 e LC.11 deste bloco [estado a reverificar na atualização] |
| K5 | Relatório de conferência das certificações de hospedagem (AWS Artifact) | Prova de conformidade da hospedagem para o nível Setor Público | **[SEM FONTE] localmente como peça produzida** — a obrigação consta das fontes da arquitetura (ADR-0011: relatório entra no kit de habilitação, ADR-0023) mas nenhum relatório existe (sem AWS) | Pendência externa: conta AWS + LC.2/LC.3 |
| K6 | Pentest externo sem achado crítico/alto aberto (LC.8) | Prova de segurança para habilitação | Inexistente — pentest é ato de terceiro sobre produção AWS futura | Pacote LC.8 deste bloco (escopo/preparação) |
| K7 | Documentos societários/jurídicos da empresa (contrato social, certidões, CNPJ) | Habilitação jurídica clássica | **[SEM FONTE]** — nenhum documento societário está nas fontes locais deste workspace; nada é reproduzido nem presumido | Pendência: fornecer a partir dos registos oficiais da empresa, fora deste pacote |
| K8 | Política comercial e preço por porte (ED-10) | Sustenta a proposta económica | Pacote local `ed-10/` existe (política/preço por porte) — estado interno a reverificar na atualização | Pacote `ed-10/` |
| K9 | Catálogo de serviços de implantação padronizados (ED-09) e plano/material de treinamento (ED-11) | Prova de capacidade de implantação | Pacotes locais `ed-09/` e `ed-11/` existem — estado interno a reverificar na atualização | Pacotes `ed-09/`, `ed-11/` |
| K10 | Página de status e observabilidade (LC.7/P3.18) como evidência de operação | Prova de operação monitorada | Inexistente em produção (sem AWS); desenho nos pacotes LC.7/P3.18 | Pendência externa: LC.7 em produção |

## Regras de atualização (M16)

1. A cada uso (licitação ou atestado), reverificar **todas** as linhas contra
   as fontes vivas e registar: data da reverificação, responsável e versão
   do kit resultante.
2. Peça marcada [SEM FONTE] ou com pendência externa **não** é substituída
   por texto genérico: ou a peça real entra, ou o kit declara a ausência.
3. O atestado (K1) nunca é redigido por nós como se emitido pelo cliente —
   ver o modelo em branco rotulado deste pacote.
4. Números (ligações, ciclos, prazos) entram só com a fonte do evento real
   (checklist LC.14, C2/C3) — nunca de projeção.
