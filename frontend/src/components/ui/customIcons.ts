import { createLucideIcon, type LucideIcon } from 'lucide-react'

/**
 * P1.29 (ICONES-E-SIMBOLOGIA §2.1 e §4.2) — icones PROPRIOS de saneamento,
 * onde o Lucide 0.303 nao cobre a metafora com um desenho distinto.
 *
 * Forma: os mesmos atributos padrao do Lucide, herdados de
 * createLucideIcon — grade 24x24, traco 2 px, pontas e juncoes
 * arredondadas, sem preenchimento (exceto um detalhe de enfase com
 * preenchimento em currentColor), cor so por currentColor. Por isso nao
 * ha arquivo .svg nem elemento svg inline no codigo: o desenho e dado
 * (nos de traco) e o proprio Lucide constroi o elemento — o que mantem
 * o check scripts/icons/check-svg.sh verde sem excecao.
 *
 * Um icone por metafora, sem texto dentro do desenho, sem referencia a
 * marca de terceiros. Os demais itens do §4.2 usam o Lucide (ver Icon.tsx):
 * so e proprio o que o Lucide nao distingue (ligacao de agua/esgoto,
 * hidrometro, rota de leitura, vazamento, manobra de valvula, baixa de
 * estoque e ordem de servico).
 */
function custom(name: string, nodes: Array<readonly [string, Record<string, string>]>) {
  // A fabrica do Lucide constroi os filhos sem chave; sem ela o React
  // avisa em desenvolvimento a cada render. A chave vai nos atributos do
  // no (o React a extrai; nao vira atributo do desenho).
  const keyed = nodes.map(([tag, attrs], index) => [tag, { ...attrs, key: String(index) }] as const)
  return createLucideIcon(name, keyed as unknown as Parameters<typeof createLucideIcon>[1])
}

export const CUSTOM_ICONS = {
  /**
   * Ligacao de agua: gota que alimenta um tubo. A gota (cima) e o tubo
   * (baixo, com duas juntas) distinguem de "droplet" generico.
   */
  'sanitation-water-connection': custom('SanitationWaterConnection', [
    ['path', { d: 'M12 2.5c-3 3.7-5 6.4-5 8.7a5 5 0 0 0 10 0c0-2.3-2-5-5-8.7Z' }],
    ['path', { d: 'M12 16.2V18' }],
    ['path', { d: 'M2 18h20' }],
    ['path', { d: 'M6 18v2.5' }],
    ['path', { d: 'M18 18v2.5' }],
  ]),
  /**
   * Ligacao de esgoto: tubo (cima, com duas juntas) com escoamento em
   * onda (baixo). A onda distingue de "waves" generico e da ligacao de
   * agua, que usa a gota.
   */
  'sanitation-sewer-connection': custom('SanitationSewerConnection', [
    ['path', { d: 'M2 7h20' }],
    ['path', { d: 'M6 7v2.5' }],
    ['path', { d: 'M18 7v2.5' }],
    ['path', { d: 'M8 9.5V13' }],
    ['path', { d: 'M16 9.5V13' }],
    ['path', { d: 'M4 17q2-3 4 0t4 0t4 0t4 0' }],
  ]),
  /**
   * Hidrometro: corpo com mostrador circular e agulha, sobre o tubo.
   * Distingue do "gauge" generico (semicirculo) usado na navegacao de
   * medicao: aqui ha corpo, mostrador e tubo.
   */
  'sanitation-water-meter': custom('SanitationWaterMeter', [
    ['rect', { x: '4', y: '3', width: '16', height: '13', rx: '3' }],
    ['circle', { cx: '12', cy: '9.5', r: '3.5' }],
    ['path', { d: 'M12 9.5 14 7.5' }],
    ['path', { d: 'M8 20v-4' }],
    ['path', { d: 'M16 20v-4' }],
    ['path', { d: 'M2 20h20' }],
  ]),
  /**
   * Rota de leitura: curva de percurso com ponto de partida, ponto de
   * passagem e ponto de chegada preenchidos (detalhe de enfase). Nao
   * usa tracejado: o traco continuo mantem leitura a 16 px.
   */
  'sanitation-reading-route': custom('SanitationReadingRoute', [
    ['path', { d: 'M4 20C9 12 15 19 20 6' }],
    ['circle', { cx: '4', cy: '20', r: '1.5', fill: 'currentColor', stroke: 'none' }],
    ['circle', { cx: '12', cy: '15', r: '1.2', fill: 'currentColor', stroke: 'none' }],
    ['circle', { cx: '20', cy: '6', r: '1.5', fill: 'currentColor', stroke: 'none' }],
  ]),
  /**
   * Vazamento: gota com fissura em zigue-zague dentro e dois respingos
   * fora, a direita. A fissura distingue de "droplet" generico.
   */
  'sanitation-leak': custom('SanitationLeak', [
    ['path', { d: 'M12 3c-3.5 4.3-6 7.4-6 10.2a6 6 0 0 0 12 0C18 10.4 15.5 7.3 12 3Z' }],
    ['path', { d: 'M10 9l1.8 2.5-1.3 2 2 2.5' }],
    ['path', { d: 'M18.5 5.5 20.5 4' }],
    ['path', { d: 'M19 9.5h2.5' }],
  ]),
  /**
   * Manobra (valvula): volante circular com quatro raios, cubo central
   * preenchido e haste com base. O Lucide nao tem valvula.
   */
  'sanitation-maneuver': custom('SanitationManeuver', [
    ['circle', { cx: '12', cy: '11', r: '7.5' }],
    ['path', { d: 'M12 3.5V18.5' }],
    ['path', { d: 'M4.5 11h15' }],
    ['circle', { cx: '12', cy: '11', r: '1.5', fill: 'currentColor', stroke: 'none' }],
    ['path', { d: 'M12 18.5V21' }],
    ['path', { d: 'M7 21h10' }],
  ]),
  /**
   * Baixa de estoque: caixa (cima) com seta de saida (baixo). Distingue
   * de "package" (material) e de "boxes" (inventario) pela seta.
   */
  'sanitation-stock-out': custom('SanitationStockOut', [
    ['path', { d: 'M3 7l9-3.5L21 7v5l-9 3.5L3 12z' }],
    ['path', { d: 'M3 7l9 3.5L21 7' }],
    ['path', { d: 'M12 10.5v5' }],
    ['path', { d: 'M12 15.5v4.5' }],
    ['path', { d: 'M8.5 17.5 12 21l3.5-3.5' }],
  ]),
  /**
   * Ordem de servico: prancheta com presilha e chave de boca em
   * diagonal. Distingue de "clipboard-list" (cadastro) e de "wrench"
   * (navegacao de OS) pela combinacao dos dois.
   */
  'sanitation-service-order': custom('SanitationServiceOrder', [
    ['rect', { x: '5', y: '4', width: '14', height: '17', rx: '2' }],
    ['rect', { x: '9', y: '2', width: '6', height: '3', rx: '1' }],
    ['path', { d: 'M9 17l5-5' }],
    ['circle', { cx: '15', cy: '10', r: '2' }],
    ['path', { d: 'M9 17l-1.5 1.5' }],
  ]),
} satisfies Record<string, LucideIcon>

export type CustomIconName = keyof typeof CUSTOM_ICONS
