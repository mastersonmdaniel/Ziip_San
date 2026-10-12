# ADR-0009 — Push automático para o GitHub e desenvolvimento fora do Google Drive

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Substitui:** D2 do plano, na parte "push só com autorização"; torna D1 (`--separate-git-dir`) desnecessária após a
  migração
- **Afeta:** revisão 17, §2 ("push manual"), §4.3 (ambiente no Google Drive), §8.6 e §8.7; achado X1 (CI preso ao
  Windows)

## Contexto

O código vive em `G:\Meu Drive\PROJETOS\Ziip SAN\sigsan`, sincronizado pelo Google Drive. Para contornar a corrupção
do `.git` pelo Drive, o repositório usa `--separate-git-dir` (D1): no Drive fica só o arquivo `.git` apontando para um
diretório em `C:\Users\shyka\.sigsan-git`. O remoto é o GitHub privado `mastersonmdaniel/Ziip_San`, com push manual e
autorizado a cada vez (D2).

Consequências observadas:

- o build não roda sobre o Drive; os scripts espelham o fonte para o disco local com `robocopy` via PowerShell
  (`scripts/lib.sh:51-58`, `scripts/ci.sh:21-22`), o que prende o CI ao Windows (X1) e pode compilar espelho velho se a
  sincronização atrasar;
- conflitos de sincronização e I/O lento;
- o `.env` local de desenvolvimento está na pasta sincronizada e, portanto, é copiado para a conta Google. Ele está
  fora do git (`.gitignore:9-11`), mas não fora do Drive. Hoje contém só valores aleatórios de desenvolvimento, mas o
  mesmo caminho levaria para o Google qualquer segredo real colocado ali;
- com push manual, o trabalho concluído pode ficar dias só na máquina local, contrariando a regra de persistência do
  projeto.

## Decisão

1. **Push automático** para `mastersonmdaniel/Ziip_San` ao fim de cada etapa concluída **com testes verdes**: o mesmo
   comando que roda o CI local faz o commit e o push quando tudo passa. Sem testes verdes, não há push.
2. Antes de cada push roda varredura de segredos (gitleaks); achado bloqueia o push.
3. O **desenvolvimento migra do Google Drive para disco local** (por exemplo, `C:\dev\ziip-san`, ou WSL2), com clone
   normal do GitHub. O **GitHub passa a ser a fonte da verdade** do código.
4. O **Drive fica só para documentos** que não pertencem ao repositório. A documentação técnica versionada (`docs/`,
   incluindo estes ADRs) segue no repositório.
5. A migração é tarefa do P0.

## Consequências

### Positivas

- Perda da máquina ou do Drive não perde mais que a etapa em andamento.
- Fim do espelho por `robocopy`: build e testes rodam no próprio clone, e os scripts ficam portáveis para o GitHub
  Actions (destrava parte de X1).
- O `.env` de desenvolvimento deixa de ir para a conta Google.
- Sem conflitos de sincronização nem `.git` fora do lugar.

### Negativas

- Push automático aumenta o risco de publicar segredo ou arquivo indevido; a varredura no item 2 e o `.gitignore`
  passam a ser controles obrigatórios, não opcionais.
- O histórico remoto recebe todo commit de etapa; erro publicado exige correção por novo commit (sem reescrever
  histórico já enviado).
- O push depende de credencial do GitHub configurada na máquina (Git Credential Manager ou chave SSH); o MCP
  `github` continua pendente de autorização e não é pré-requisito.
- Durante a transição haverá duas cópias (Drive e disco local); é preciso um corte claro para não editar as duas.

### O que muda no código

- `scripts/lib.sh` e `scripts/ci.sh`: sai o espelho por `robocopy`; `SRC` passa a ser o próprio clone.
- `scripts/dev-up.sh` deixa de copiar `docker-compose.yml` e `.env` para um diretório de execução.
- Novo passo de encerramento de etapa: CI local → varredura de segredos → commit → push.
- `README.md` e diário de bordo: novo caminho de trabalho e procedimento de clone.

### O que muda no roadmap

- Migração e push automático entram no início do P0, antes do CI no GitHub Actions.

## Alternativas consideradas

- **Manter push manual com autorização (D2).** Rejeitada: deixa o código sem cópia externa entre autorizações.
- **Manter o código no Drive com `--separate-git-dir`.** Rejeitada: preserva o espelho por `robocopy`, os conflitos e o
  `.env` sincronizado com o Google.
- **Backup do clone local por outra ferramenta, sem push.** Rejeitada: o GitHub já é o remoto e é necessário para o CI.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Clonar em disco local (ou WSL2), conferir que o build e os 973 + 134 testes passam no clone | P0 |
| Remover o espelho por `robocopy` dos scripts; ajustar `dev-up.sh` e `README.md` | P0 |
| Passo de encerramento de etapa com CI, gitleaks, commit e push automático | P0 |
| Retirar do Drive a cópia de código e o `.env`; gerar novos valores de desenvolvimento no clone local | P0 |
| Registrar o corte (data, commit de referência) no diário de bordo | P0 |
