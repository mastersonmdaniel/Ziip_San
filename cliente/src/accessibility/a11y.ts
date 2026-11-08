/**
 * Acessibilidade do aparelho no app do cliente (ED-39; SG-782): o app
 * respeita as configuracoes do aparelho em vez de substitui-las —
 * fonte dinamica (sem travar tamanho de texto), reducao de movimento
 * (sem animacao essencial), rotulos completos para TalkBack/VoiceOver
 * e alvos de toque minimos (48dp Android / 44pt iOS). A conferencia
 * completa e o portao de liberacao vivem no servidor
 * (AccessibilityGate) e no ensaio em aparelho (POC-55).
 */
export const MIN_TOUCH_TARGET_DP = 48;
export const MIN_TOUCH_TARGET_PT = 44;

/** Escala de fonte efetiva: a do aparelho, sem teto imposto pelo app. */
export function effectiveFontScale(deviceFontScale: number): number {
  if (!Number.isFinite(deviceFontScale) || deviceFontScale <= 0) {
    throw new Error('escala de fonte do aparelho invalida');
  }
  return deviceFontScale;
}

/** Com reducao de movimento ligada no aparelho, animacao essencial e desligada. */
export function animationsEnabled(prefersReducedMotion: boolean): boolean {
  return !prefersReducedMotion;
}

export interface InteractiveElement {
  label: string;
  width: number;
  height: number;
  platform: 'android' | 'ios';
}

/** Elemento interativo sem rotulo ou abaixo do alvo minimo reprova na conferencia SG-782. */
export function elementPassesA11y(el: InteractiveElement): boolean {
  const min = el.platform === 'android' ? MIN_TOUCH_TARGET_DP : MIN_TOUCH_TARGET_PT;
  return el.label.trim() !== '' && el.width >= min && el.height >= min;
}
