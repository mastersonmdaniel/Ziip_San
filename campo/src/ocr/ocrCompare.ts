/**
 * Espelho em TypeScript de br.com.ziipsan.operations.metering.field.OcrComparison
 * (ED-18): a mesma regra roda no aparelho (critica imediata, offline) e no servidor
 * (decisao que vale). Os vetores dos testes dos dois lados sao os mesmos; divergir
 * os dois e defeito — se um mudar, o outro muda junto.
 */
export type OcrMode = 'OFF' | 'SUGGEST' | 'REQUIRE';
export type OcrVerdict = 'DISABLED' | 'MATCH' | 'SUGGESTION' | 'REVIEW';

export function divergentDigits(a: number, b: number, meterDigits: number): number {
  // Largura comum aos dois valores (espelho exato da correcao no Java): larguras
  // separadas subcontavam quando um valor passava dos digitos do mostrador.
  const width = Math.max(meterDigits, String(a).length, String(b).length);
  const sa = String(a).padStart(width, '0');
  const sb = String(b).padStart(width, '0');
  let count = 0;
  for (let i = 0; i < sa.length; i++) if (sa[i] !== sb[i]) count++;
  return count;
}

function historyDiverges(typed: number, previous: number | null, meterDigits: number): boolean {
  if (previous === null || typed >= previous) return false;
  const capacity = 10 ** meterDigits;
  return previous * 10 < capacity * 9;
}

export function evaluateOcr(
  mode: OcrMode,
  toleranceDigits: number,
  meterDigits: number,
  typedValue: number | null,
  ocrValue: number | null,
  previousReading: number | null,
): OcrVerdict {
  if (mode === 'OFF') return 'DISABLED';
  if (typedValue === null) return 'MATCH';
  if (historyDiverges(typedValue, previousReading, meterDigits)) return 'REVIEW';
  if (ocrValue === null) return mode === 'REQUIRE' ? 'REVIEW' : 'MATCH';
  const divergent = divergentDigits(typedValue, ocrValue, meterDigits);
  if (divergent <= toleranceDigits) return 'MATCH';
  return mode === 'REQUIRE' ? 'REVIEW' : 'SUGGESTION';
}
