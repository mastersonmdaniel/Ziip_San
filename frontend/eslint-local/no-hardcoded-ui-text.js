/**
 * P1.18 (PLT-25) — regra local de lint: recusa texto fixo de interface
 * fora do catálogo de mensagens, NOS ARQUIVOS QUE ADOTARAM O CATÁLOGO.
 *
 * Escopo viável, deliberado (ver o relatório da P1.18): aplicar a regra a
 * todo o frontend de uma vez quebraria o build — o código pré-existente
 * tem centenas de literais (telas de cadastro, GIS, ajuda) que ainda não
 * passaram pelo catálogo, como as cores literais antes da migração da
 * P0.36. Por isso a regra é opt-in por arquivo: ela só age num arquivo
 * que importa '@i18n/messages'. Arquivo que ainda não adotou o catálogo
 * não é sinalizado; arquivo que adotou não pode voltar a pôr literal no
 * JSX. Código novo adota o catálogo; a migração do restante é follow-up
 * tela a tela, sem travar o build inteiro no meio.
 *
 * O que é sinalizado (só em arquivo opt-in):
 *   - texto literal entre tags JSX (JSXText com letra);
 *   - literal em atributo de texto de interface: aria-label, title,
 *     placeholder, alt.
 * O que NÃO é sinalizado (para não gerar falso positivo):
 *   - className, href, id, data-*, nomes de ícone (não são texto ao usuário);
 *   - expressões {t('...')} e {variavel} (catálogo ou dado, não literal);
 *   - literais fora do JSX (o catálogo de nomes de menu, por exemplo, já
 *     usa t() na definição — mas a regra não os exige; a migração deles é
 *     manual e coberta pelo teste de cobertura de chaves).
 */
const UI_TEXT_ATTRIBUTES = new Set(['aria-label', 'title', 'placeholder', 'alt'])
const HAS_LETTER = /[A-Za-zÀ-ú]/

function importsCatalog(node) {
  if (node.source == null || typeof node.source.value !== 'string') return false
  const from = node.source.value
  return from === '@i18n/messages' || from.endsWith('/i18n/messages') || from === '../i18n/messages'
}

const rule = {
  meta: {
    type: 'problem',
    docs: {
      description:
        'Recusa texto fixo de interface fora do catálogo (P1.18): em arquivo que importa @i18n/messages, use t() em vez de literal no JSX.',
    },
  },
  create(context) {
    let optedIn = false
    return {
      ImportDeclaration(node) {
        if (importsCatalog(node)) optedIn = true
      },
      JSXText(node) {
        if (!optedIn) return
        const text = node.value.replace(/\s+/g, ' ').trim()
        if (text !== '' && HAS_LETTER.test(text)) {
          context.report({
            node,
            message:
              'Texto fixo de interface fora do catálogo (P1.18): use t() de @i18n/messages em vez de literal no JSX.',
          })
        }
      },
      JSXAttribute(node) {
        if (!optedIn) return
        if (!UI_TEXT_ATTRIBUTES.has(node.name.name)) return
        if (node.value != null && node.value.type === 'Literal' && typeof node.value.value === 'string') {
          context.report({
            node,
            message:
              'Texto fixo de interface fora do catálogo (P1.18): use t() de @i18n/messages em vez de literal em ' +
              node.name.name +
              '.',
          })
        }
      },
    }
  },
}

export default { rules: { 'no-hardcoded-ui-text': rule } }
