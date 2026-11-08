# Material online de treinamento — ZiipSan (ED-11)

O aceite da ED-11 pede "material online". Este documento define o que é
esse material, onde ele vive e como é publicado — sem prometer canal que
o projeto não tem.

## Formato e publicação (a base real: a ajuda do produto, P1.21)

O material online de treinamento é conteúdo versionado no repositório,
no mesmo regime da ajuda do produto da P1.21: arquivos em
`docs/treinamento/` (este pacote) e, para a ajuda contextual dentro do
produto, o `content/help` validado no CI e publicado pelo `migrate` sem
nova imagem (mecanismo da P1.21, com busca em português). Ou seja:

- **fonte única no repositório** — material não vive em arquivo solto
  fora do versionamento; mudou o produto, muda o material no mesmo
  ciclo, e o CI confere cabeçalho, links e rotas como faz com a ajuda;
- **por módulo do catálogo** — cada módulo de treinamento tem o seu
  roteiro (`ROTEIROS-POR-MODULO.md`), escrito sobre as funções reais da
  área, com os exercícios executados no tenant de pré-treinamento;
- **sem dado de cliente** — exemplos do material são sintéticos ou do
  clone mascarado; nenhum exemplo usa nome, documento ou endereço real
  (a mesma proibição do ambiente vale para o material).

## O que cada módulo publica

| Peça | Conteúdo |
|---|---|
| Roteiro do módulo | objetivos por unidade (nas cinco fases do plano), demonstrações e exercícios com o resultado esperado de cada um |
| Guia de exceções | os casos da unidade `EXCECOES_E_CONTROLES`: o que o sistema recusa, por quê, e onde a recusa fica registrada (catálogo de erros explicados da P1.22, quando a área o usa) |
| Registro de conclusão | o modelo `REGISTRO-CONCLUSAO.template.csv`, preenchido por turma |
| Material da TI | `CAPACITACAO-TI.md`, com a prática de cada tópico `handsOn` descrita como exercício no tenant de pré-treinamento |

## O que este pacote NÃO entrega (honestidade de canal)

- **LMS / portal de cursos com matrícula, progresso e certificado
  automáticos não consta de nenhuma fonte do projeto.** O material
  online desta entrega é o conteúdo versionado e publicado pelo regime
  da P1.21; transformar isso numa plataforma de cursos é decisão e
  escopo novos: **[A DEFINIR PELO DONO: canal de publicação do material
  online além da ajuda do produto — LMS próprio, portal ou nenhum]**.
- **Vídeos e gravações de aula** não foram produzidos nem prometidos:
  nenhuma fonte os pede, e produzi-los sem o produto em tela seria
  material sobre um sistema que o aluno não pode conferir.
- **Certificado de conclusão por aluno** não é emitido por este pacote:
  a evidência de conclusão é o registro por módulo (ids de usuário,
  horas e unidades), que é o que o gate da jornada consome.

## Versionamento

O material segue a versão do produto: roteiro de módulo cita a tarefa
de origem de cada função treinada, e a revisão do material entra na
mesma rodada em que a função muda — o dono da revisão é quem revisa a
tarefa que mudou a função (o portão de revisão cruzada do projeto vale
para o material como vale para o código).
