# ADR-0017 — Temas claro, escuro e do sistema, com padrões Azul Água e Verde Água

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0016 (marca), P0.16 (CSP); detalhes em `docs/arquitetura/MARCA-E-TEMAS.md` §4
- **Ajuste de 12/10/2026 (plano v2.2), antes de qualquer implementação:** item 7 registra a aprovação dos valores pelo
  dono do produto; ids dos padrões em inglês (plano §8, Idioma).

## Contexto

Os tokens de cor estão em `frontend/src/index.css:11-58`, com tema escuro, consumidos por `tailwind.config.js`. Há um
só padrão (primária `201 96% 32%`). `frontend/index.html:7` repete a cor em `theme-color` com valor divergente
(`#0361a0`). `frontend/src/hooks/useTheme.tsx` aplica o tema num `useEffect`, e a tela pisca ao carregar; a escolha fica
só no `localStorage`, sem o modo "do sistema" como opção. `Badge.tsx` e `Alert.tsx` usam classes de cor literais. O
contraste foi calculado à mão; axe em jsdom não o mede.

## Decisão

1. Modos **claro, escuro e do sistema**; padrões de cores **Azul Água** e **Verde Água** num **registro** extensível
   pelo desenvolvimento. Não há editor para o cliente.
2. **Tokens semânticos** (base, ação, estado, `chart-*`, `map-*`); nenhum componente usa cor literal, com lint.
3. Cada usuário escolhe modo e padrão; a preferência fica **no servidor**, no perfil. O tenant define o padrão de
   fábrica e pode restringir os padrões disponíveis.
4. A cor do tenant **não** substitui a primária da interface (ADR-0016).
5. Tema aplicado **antes da primeira pintura** por script inline estático, autorizado na CSP por hash.
6. **Contraste WCAG 2.1 AA verificado no CI** para cada padrão nos dois modos; falha bloqueia o merge.
7. Valores iniciais em MARCA-E-TEMAS §4.2, **aprovados pelo dono em 12/10/2026; ajustes finos permitidos após ver as
   telas, mantendo o teste de contraste**. No registro, os padrões têm ids `aqua-blue` (Azul Água) e `aqua-green`
   (Verde Água); o rótulo exibido segue em português.

## Consequências

### Positivas

- A preferência acompanha o usuário entre dispositivos.
- Padrão novo entra por arquivo de registro, com contraste garantido pelo CI.
- Fim do piscar ao carregar e da cor duplicada no `index.html`.

### Negativas

- Componentes com cor literal precisam ser migrados antes de a regra de lint valer.
- Cada padrão multiplica as verificações visuais (padrão × modo).
- O hash do script precisa acompanhar a CSP a cada mudança do script.

### O que muda no código

- `frontend/src/theme/palettes/*.json` e gerador de `palettes.css` e `palette-ids.json`; `index.css` passa a ter só os
  tokens comuns; Tailwind com `darkMode: ['selector', '[data-mode="dark"]']`.
- `useTheme.tsx` com três modos e padrão; script inline no `index.html`.
- `user_preferences` com RLS e `GET/PUT /api/v1/me/preferences`; chaves `theme.*` em `tenant_settings`.
- Teste de contraste em Vitest e Playwright com axe nos dois modos.

### O que muda no roadmap

- Tokens, registro, script e teste de contraste no P0, junto da CSP (P0.16). Preferência no servidor e padrão do
  tenant no P1, com o console (P1.6).

## Alternativas consideradas

- **Só claro e escuro, sem padrões.** Rejeitada: o dono pediu escolha de padrão e um registro extensível.
- **Preferência só no `localStorage`.** Rejeitada: perde a escolha ao trocar de dispositivo ou limpar o navegador.
- **Editor de cores para o tenant.** Rejeitada: não há como garantir contraste em combinações livres.
- **Aplicar o tema no React.** Rejeitada: pisca na primeira pintura.
- **BFF injetando o tema no `index.html` a cada requisição.** Rejeitada por ora: acopla a entrega da casca ao BFF; o
  cache local resolve o caso comum.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Tokens semânticos, apelidos de compatibilidade e lint contra cor literal (`Badge.tsx`, `Alert.tsx`) | P0 |
| Registro com Azul Água e Verde Água, gerador e `darkMode` por atributo | P0 |
| Script inline sem piscar com hash na CSP e teste do hash | P0 |
| Teste de contraste no CI (Vitest e Playwright com axe) | P0 |
| Valores aprovados pelo dono em 12/10/2026 aplicados ao registro; ajuste fino após ver as telas, com o teste de contraste verde | P0 |
| Preferência no servidor; padrão e restrição do tenant no console | P1 |
