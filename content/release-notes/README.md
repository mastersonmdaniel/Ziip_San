# Release notes do cliente — P0.41

Um arquivo por versão lançada: `content/release-notes/<version>.yaml`, onde `<version>` é
o SemVer da tag (sem `v`, sem `-SNAPSHOT`) e é IGUAL ao nome do arquivo. O arquivo da
versão é criado no momento da release, com a versão da tag — nunca antes, nunca com
versão inventada. Não existe arquivo `UNRELEASED.yaml`: trabalho não lançado não tem
release note.

## Esquema

```yaml
version: "1.2.0"          # obrigatório, SemVer, igual ao nome do arquivo
date: "2026-11-20"        # obrigatório, YYYY-MM-DD (data da release)
notes: "..."              # opcional, texto livre
entries:                  # obrigatório, não vazio
  - type: security        # feature | fix | security | legislation | regulatory-parameter
    title: "Corrige ..."  # obrigatório, não vazio
    commits: ["abc1234"]  # opcional, SHAs (completos ou >= 7 chars) do(s) commit(s)
    issue: "PLT-123"      # opcional
```

O esquema formal está em `schema.json` (JSON Schema, para ferramentas); a validação do
CI (`scripts/check-changelog.py`) implementa as mesmas regras em Python stdlib + PyYAML,
sem depender de validador de JSON Schema.

## Regra que bloqueia a release

Um commit marcado como segurança, legislação ou parâmetro regulatório SÓ entra na release
se houver um item correspondente no changelog:

- Marcadores no assunto do commit (case-insensitive): `[security]`, `[legislation]`,
  `[regulatory-parameter]`, ou os prefixos `security:`, `legislation:`,
  `regulatory-parameter:`.
- "Correspondente" = um item com o MESMO `type` cuja lista `commits` referencia o SHA
  do commit (completo ou prefixo de 7+ caracteres).

Sem item correspondente, `check-changelog.py --commits-file ...` falha e o job `changelog`
do CI barra a release. Marcar um commit sem escrever o item é, portanto, um erro de
release detectável — não uma convenção.

**Não invente decisão do dono aqui:** a lista acima é de MARCAÇÃO, não de conteúdo. O
conteúdo do item (o que mudou, para quem) é escrito por quem faz a release, a partir do
commit real — nunca gerado a partir de regra presumida.
