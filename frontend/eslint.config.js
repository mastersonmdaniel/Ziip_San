// ESLint 9 (configuração flat). TypeScript com typescript-eslint, regras de hooks do React,
// react-refresh (Vite) e jsx-a11y: acessibilidade é obrigação legal (LBI, art. 63), não estética.
import js from '@eslint/js'
import globals from 'globals'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

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
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
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
