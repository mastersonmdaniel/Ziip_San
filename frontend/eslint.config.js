// ESLint 9 (configuração flat). TypeScript com typescript-eslint, regras de hooks do React,
// react-refresh (Vite) e jsx-a11y: acessibilidade é obrigação legal (LBI, art. 63), não estética.
import js from '@eslint/js'
import globals from 'globals'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import ziipsan from './eslint-local/no-literal-color.js'

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, jsxA11y.flatConfigs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      ziipsan,
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // P0.36: cor literal (paleta Tailwind, #hex, rgb()/hsl() numerico) e proibida
      // em componentes — so tokens semanticos. O modulo gerado da marca e excecao:
      // ele E a copia do pacote (ver bloco abaixo).
      'ziipsan/no-literal-color': 'error',
    },
  },
  {
    // Copia gerada do pacote de marca (P0.34): os #hex dela SAO o pacote, nao
    // literais espalhados — o lint nao se aplica aqui. O mesmo vale para o
    // registro gerado de padroes (P0.36): os #hex sao os theme-colors do §4.2.
    files: ['src/brand/brand.generated.ts', 'src/theme/palettes.generated.ts'],
    rules: { 'ziipsan/no-literal-color': 'off' },
  },
  {
    // Declarações de tipo: aumento de interfaces de bibliotecas (ex.: matchers do vitest-axe) é
    // declaração vazia por natureza.
    files: ['**/*.d.ts'],
    rules: { '@typescript-eslint/no-empty-object-type': 'off', '@typescript-eslint/no-unused-vars': 'off' },
  },
  {
    // Testes e configuração de teste: globais do Vitest e mocks com tipos frouxos.
    files: ['**/*.test.{ts,tsx}', 'src/test/**'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: { 'react-refresh/only-export-components': 'off' },
  },
)
