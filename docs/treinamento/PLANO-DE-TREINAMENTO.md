# Plano de treinamento — ZiipSan (ED-11)

Documento publicado do plano de treinamento da implantação. Ele é a
contrapartida, para o passo `TRAINING`, da metodologia de implantação da
ED-09 (`docs/implantacao/METODOLOGIA-IMPLANTACAO.md`): documento e código
usam o mesmo vocabulário — as chaves de módulo e de unidade são as de
`br.com.ziipsan.platform.training.TrainingPlan` do módulo da plataforma.

## A régua: 40 h por módulo

O aceite da ED-11 fixa **40 h por módulo** — é o único número de carga
horária com fonte no projeto. Cada módulo do catálogo tem um plano de
exatamente 40 h, validado por regra (`TrainingPlan.validate`): plano com
39 h ou 41 h não é plano deste modelo. As horas se distribuem em cinco
fases canônicas, na mesma ordem em todo módulo (**desenho do modelo**,
como as janelas de dias dos planos-modelo de 60/90 dias da ED-09; o plano
concreto de um módulo pode redistribuir as horas entre as suas unidades
mantendo o total):

| Unidade (chave) | Horas no plano-padrão | O que acontece |
|---|---|---|
| `FUNDAMENTOS` — fundamentos e mapa do módulo | 8 h | conceitos, mapa do módulo no produto e vocabulário |
| `OPERACAO_GUIADA` — operação guiada | 12 h | operação demonstrada e acompanhada, no tenant de pré-treinamento |
| `PRATICA_SUPERVISIONADA` — prática supervisionada | 12 h | exercícios do roteiro do módulo, no tenant de pré-treinamento |
| `EXCECOES_E_CONTROLES` — exceções, controles e auditoria | 4 h | casos de exceção do módulo, controles e trilha de auditoria |
| `AVALIACAO_E_ENCERRAMENTO` — avaliação e encerramento | 4 h | avaliação de aproveitamento e encerramento formal do módulo |

**Aproveitamento:** o critério mínimo de aproveitamento da avaliação
(nota ou corte) **não consta de nenhuma fonte do projeto** e não foi
inventado aqui — **[A DEFINIR PELO DONO: critério mínimo de
aproveitamento da avaliação de cada módulo]**. Até ele existir, a
conclusão do módulo mede entrega e participação (ver "Conclusão",
abaixo), nunca nota.

## Catálogo de módulos

Cada módulo treina uma área real do produto; a coluna de origem é a
tarefa do plano v3.0 que constrói a área, e o público usa os perfis de
fábrica da P1.37 (e os papéis mínimos da P0.13 onde citados). Nenhum
módulo existe sem a função correspondente existir no ZiipSan.

| Módulo (chave) | Área | Origem no plano | Público principal |
|---|---|---|---|
| `CADASTRO` | Cadastro e unidade consumidora | P1.3, P1.2, P1.31, P1.41, P1.40 | atendente, supervisor comercial, gestor |
| `MEDICAO_CONTA` | Medição, ciclo e conta | P2.5, P2.7, P2.20, P2.29 | leiturista, faturista, supervisor comercial |
| `ARRECADACAO` | Arrecadação: retorno, Pix e contas a receber | P3.1, P3.2, P3.29, P2.5 | analista de arrecadação, faturista, contador |
| `FISCAL` | Fiscal: NFAg-e, NFS-e e documentos fiscais | P1.8, P2.6, P2.13, P3.6, P2.33, P3.7 | faturista, contador, gestor |
| `PORTAL_ATENDIMENTO` | Portal do consumidor e atendimento | P3.3, P3.20, P1.36, P3.36, P3.35 | atendente, ouvidor, supervisor comercial |
| `OS_CAMPO` | Ordens de serviço e operação de campo | P1.31, P1.35, P3.4, P2.28 | programador de OS, executor de campo, almoxarife, operador de estação |
| `GIS_CADASTRO_TECNICO` | Cadastro técnico e GIS | P1.26, P1.27, P3.25, P3.26, P3.27, P2.21–P2.23 | editor do cadastro técnico, revisor do cadastro técnico, gestor |
| `PERDAS_QUALIDADE` | Perdas, qualidade e certificações | P1.32, P3.28, P3.27 | analista de perdas, laboratorista, responsável técnico da qualidade, auditor interno |
| `PLATAFORMA_IMPLANTACAO` | Plataforma, parâmetros e jornada de implantação | P1.6, P0.19, P0.13, P1.37, ED-09 | ADMIN, gestor |
| `PRIVACIDADE_SEGURANCA` | Privacidade, LGPD e segurança de acesso | P3.40, P3.10, P0.11, P0.15, P3.17, P0.13 | encarregado de dados, auditor interno, ADMIN |
| `TI_CLIENTE` | Capacitação da TI do cliente | P0.29, P2.26, P3.37, P0.28, P3.33, P3.11, LC.15 | ADMIN / equipe de TI do cliente |

## Escopo por implantação

Um tenant não cursa necessariamente o catálogo inteiro: o **escopo
requerido** de uma implantação é o conjunto de módulos declarado para
ela, conforme as áreas que o tenant opera. Duas regras não negociam:

1. o escopo nunca é vazio (gate sem escopo concluiria qualquer coisa);
2. o módulo `TI_CLIENTE` está sempre no escopo — a capacitação da TI é
   item próprio do aceite da ED-11, não módulo optativo.

O escopo concreto de cada cliente é decisão da implantação:
**[A DEFINIR COM O CLIENTE: módulos requeridos do escopo de treinamento
de cada implantação, a partir das áreas que o tenant opera]** e
**[A DEFINIR COM O CLIENTE: participantes de cada turma, por perfil —
o cliente indica os usuários; o registro guarda só os ids, sem nome]**.

## Onde o treinamento acontece

Toda a operação guiada e a prática acontecem no **tenant de
pré-treinamento** (ver `AMBIENTE-PRE-TREINAMENTO.md`): massa sintética ou
clone mascarado da ED-09, sem dado pessoal real e com a emissão fiscal
de produção desligada. Treinamento em tenant de produção ou de
homologação não conta para a conclusão de módulo nenhum.

## Conclusão de um módulo

Um módulo está concluído quando, cumulativamente (regra em
`TrainingCompletion`):

- o plano do módulo é o plano válido de 40 h;
- todas as unidades do plano foram entregues;
- as horas entregues somam pelo menos 40 h;
- há pelo menos um participante inscrito e todos os inscritos
  concluíram — participantes referenciados pelo id de usuário do
  tenant, nunca por nome.

O registro de cada módulo usa o modelo
`REGISTRO-CONCLUSAO.template.csv` deste pacote e é gravado na governança
da plataforma (`platform.training_module_completion`, SQL pendente da
ED-11).

## O gate na jornada de implantação

Com todos os módulos do escopo concluídos, a capacitação da TI completa
em conteúdo e o tenant de pré-treinamento pronto, o passo `TRAINING` da
jornada de implantação do console da P1.6 (PLT-21) pode ser concluído —
a régua é `TrainingGate`, e o checklist operacional está em
`CHECKLIST-GATE-TREINAMENTO.md`. Sem isso, o tenant não está pronto
para produção por este passo, como nos demais gates da metodologia
da ED-09.
