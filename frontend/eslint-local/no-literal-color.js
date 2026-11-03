/**
 * P0.36/P0.46 — regra local de lint: recusa cor literal em componentes.
 * Cores de paleta do Tailwind (bg-red-500, text-green-900, ...), #hex e
 * rgb()/hsl() numerico sao proibidos; so valem tokens semanticos
 * (bg-primary, text-success, hsl(var(--...))). Preto/branco de scrim
 * (bg-black/50) e excecao documentada: nao sao cores de paleta.
 * O plugin e carregado por eslint.config.js como 'ziipsan'.
 */
const PALETTE_CLASS = /\b(?:bg|text|border|ring|divide|outline|decoration|accent|from|via|to|placeholder|caret|fill|stroke)-(red|green|blue|amber|yellow|orange|purple|pink|gray|slate|emerald|teal|cyan|lime|indigo|violet|fuchsia|rose|stone|neutral|zinc)-\d/
const HEX = /#[0-9a-fA-F]{3,8}\b/
const NUMERIC_COLOR_FN = /\b(?:rgb|hsl)a?\(\s*\d/

function checkString(value, node, context) {
  if (PALETTE_CLASS.test(value)) {
    context.report({ node, message: 'Cor literal de paleta do Tailwind proibida (P0.36): use um token semantico (bg-primary, text-success, ...).' })
  } else if (HEX.test(value)) {
    context.report({ node, message: 'Cor #hex literal proibida (P0.36): use um token semantico (hsl(var(--...))).' })
  } else if (NUMERIC_COLOR_FN.test(value)) {
    context.report({ node, message: 'Cor rgb()/hsl() numerica proibida (P0.36): use um token semantico (hsl(var(--...))).' })
  }
}

const rule = {
  meta: { type: 'problem', docs: { description: 'Recusa cor literal em componentes (P0.36/P0.46)' } },
  create(context) {
    return {
      Literal(node) {
        if (typeof node.value === 'string') checkString(node.value, node, context)
      },
      TemplateLiteral(node) {
        for (const quasi of node.quasis) checkString(quasi.value.cooked ?? '', node, context)
      },
    }
  },
}

export default { rules: { 'no-literal-color': rule } }
