# RNF novo — RTO de restauração de um tenant (P3.33)

> **Natureza deste arquivo:** a P3.33 exige "RNF novo de RTO de restauração de um tenant". O texto abaixo é a **linha pronta para entrar** em `docs/arquitetura/REQUISITOS-NAO-FUNCIONAIS.md`, seção 2 (Disponibilidade e continuidade), como **RNF-72** — o próximo número livre na sequência do documento (o maior em uso é o RNF-71, conferido no arquivo em 07/11/2026). A numeração é **proposta desta frente** e deve ser confirmada na integração, como as demais convenções declaradas das frentes irmãs; o conteúdo da linha não depende do número.

## Linha proposta

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-72 | Restauração seletiva de **um** tenant a um instante anterior, sem afetar os demais tenants do cluster: tempo de restauração por tenant (**RTO por tenant**) **registrado a cada execução e a cada ensaio**, **sem meta antes da LC.3**; a meta é fixada na LC.3 a partir dos tempos registrados | Ensaio cronometrado com tenant sintético (início da aplicação → fim da conferência da fotografia pós-restauração), com teste de isolamento dos demais tenants durante a restauração | Runbook RB-07, scripts de operação da P3.33 | P3 (ensaio local, sem meta); lançamento (meta, LC.3) | Não medido: depende do ensaio da P3.33 no ambiente do dono |

## Por que sem meta agora (e por que isso não é afrouxar)

- O aceite da P3.33 manda literalmente: "ensaio documentado com tenant sintético, com o RTO por tenant registrado, **sem meta antes da LC.3**".
- É o mesmo desenho do RTO do cluster: o RNF-15 (RTO ≤ 4 h) é **meta de lançamento** (LC.3/LC.12) e, antes dele, a P0.28 e a GL.3 ensaiam "com tempo registrado, sem meta". O RTO **por tenant** segue a mesma régua: medir primeiro, fixar a meta na LC.3 com os tempos reais dos ensaios — fixar um número agora seria inventá-lo.
- O que já vale desde já, sem depender de meta: restauração só por `PLATFORM_OWNER` com step-up, com aprovação do tenant (ADR-0020), em janela, com registro na trilha, e o isolamento dos demais tenants provado no ensaio.
