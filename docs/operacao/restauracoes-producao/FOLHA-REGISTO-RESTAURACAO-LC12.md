# Folha de Registo da restauração da produção — LC.12 (MODELO EM BRANCO)

> **Produtor do modelo:** sessão `95ff17bd-dd7b-4269-a848-c235bad7f4be` (papel: Produtor), 09/11/2026.
> Isto é um **modelo em branco**. Nenhum campo está preenchido porque nenhuma
> restauração de produção aconteceu (sem AWS, sem RDS, sem cliente — item 34
> do DECISOES-DO-DONO.md). A folha real nasce preenchida **na execução**, com
> valores medidos, e é guardada ao lado dos artefactos da execução.
> Roteiro associado: `docs/operacao/ROTEIRO-RESTAURACAO-PRODUCAO-LC12.md`.

```
Folha de Registo - LC.12 Restauracao da producao (PITR do RDS)
Data/hora de abertura da janela: ____  Fuso do relogio unico: ____
Cliente/piloto: ____  Tenant(s): ____

PAPEIS (nome / cargo / contacto)
  Executor da restauracao: ____
  Verificador independente: ____
  Cronometrista/registador: ____
  Responsavel do cliente: ____
  Dono do produto: ____

PRE-CONDICOES (roteiro seccao 1) - SIM | NAO, com evidencia
  PC-1 LC.3 / RDS com PITR (instancia, janela PITR, instante restauravel): ____
  PC-2 LC.10 / carga real conciliada (referencia do registo): ____
  PC-3 Janela autorizada antes do corte LC.13 (quem autorizou / quando): ____
  PC-4 Papeis presentes: ____
  PC-5 Instancia de destino isolada (identificador): ____
  PC-6 Segredos so via LC.4 (declaracao do executor): ____

INSTANTE-ALVO DO PITR: ____  Fundamento da escolha: ____

TEMPOS MEDIDOS (inicio -> fim, duracao) x META
  T0 disparo do PITR: ____
  subida e sanidade: ____
  contagem de registros: ____
  checksum: ____
  teste de RLS: ____
  T1 veredito: ____
  TEMPO DO RTO (T1 - T0): ____ s   Meta RNF-15: <= 14400 s (4 h)
  Dentro do RTO: SIM | NAO (se NAO, etapa do excesso e decisao registada): ____

FOTOGRAFIA DA ORIGEM (baseline)
  Tabelas contadas: ____  Registos (soma): ____
  Checksum global (SHA-256 do TSV por tabela): ____
  Artefacto: ____  Desvio/substituto, se houve: ____

CONTAGEM NA RESTAURADA
  Tabelas contadas: ____  Registos (soma): ____
  Artefacto: ____  Divergencias (uma a uma, explicadas): ____

CHECKSUM NA RESTAURADA
  Checksum global (SHA-256 do TSV por tabela): ____
  Artefacto: ____  Confere com a baseline: SIM | NAO (divergencias): ____

TESTE DE RLS (como role de aplicacao, no banco restaurado)
  Sem contexto = 0 / contexto proprio = so as linhas dele / contexto alheio = 0:
    OK | FALHOU | INCONCLUSIVO
  Artefacto: ____

DESTINO DA INSTANCIA RESTAURADA: ____ (quem decidiu: ____)

VEREDITO
  Restauracao concluida: SIM | NAO
  Apta como base do corte LC.13: SIM | NAO
  Assinaturas: Executor ____  Verificador ____  Cliente ____  Dono do produto ____

DESVIOS E OBSERVACOES
  ____
```

Campos que só existem no evento real (instante-alvo, identificadores AWS,
nomes, tempos, contagens, hashes) ficam em branco aqui por desenho: preenchê-los
agora seria inventar o evento.
