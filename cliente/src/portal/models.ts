/**
 * Logica pura de exibicao/validacao das telas do portal (sem DOM, sem rede).
 * Cada regra espelha a validacao do service dono do contrato — a tela
 * recusa localmente o que o servidor recusaria, sem inventar regra nova.
 */
import type {
  AnonymousReportRequest,
  BillSimulationRequest,
  GuestInvoiceRequest,
  InstallmentSimulationRequest,
  ReportCategory,
} from './transport.ts';

export const REPORT_CATEGORIES: ReportCategory[] = [
  'LEAK', 'NO_WATER', 'LOW_PRESSURE', 'WATER_QUALITY', 'SEWAGE', 'OTHER',
];

/** GuestPaymentService/LinkingCode: codigo e sufixo sao obrigatorios no corpo. */
export function validateGuest(req: GuestInvoiceRequest): string | null {
  if (!req.linkingCode.trim()) return 'Informe o código de vinculação.';
  if (!req.documentSuffix.trim()) return 'Informe o final do documento.';
  return null;
}

/**
 * AnonymousReportService.Report: categoria, localizacao, FOTO e descricao
 * obrigatorios; contato opcional (correcao D6 da ED-38).
 */
export function validateReport(req: AnonymousReportRequest): string | null {
  if (!REPORT_CATEGORIES.includes(req.category)) return 'Informe a categoria do relato.';
  if (!req.locationText.trim()) return 'Informe a localização.';
  if (!req.photoRef.trim()) return 'A foto é obrigatória no relato.';
  if (!req.description.trim()) return 'Informe a descrição.';
  return null;
}

/** Poc69SelfCareService.simulateBill: consumo >= 0 e estrutura completa. */
export function validateBillSimulation(req: BillSimulationRequest): string | null {
  if (!Number.isFinite(req.consumptionM3) || req.consumptionM3 < 0) {
    return 'Informe um consumo maior ou igual a zero.';
  }
  const t = req.tariff;
  if (!Number.isFinite(t.minimumFee) || !Number.isFinite(t.sewagePercent)
      || !t.bands.length || t.bands.some((b) => !Number.isFinite(b.upToM3) || !Number.isFinite(b.pricePerM3))) {
    return 'A estrutura tarifária vigente é obrigatória para simular [SEM FONTE na tela: vem do tenant].';
  }
  // Poc69SelfCareService.TariffBand (construtor compacto): pricePerM3 < 0 e recusado.
  if (t.bands.some((b) => b.pricePerM3 < 0)) {
    return 'Informe um preço por m³ maior ou igual a zero.';
  }
  return null;
}

/** Poc69SelfCareService.simulateInstallments: divida > 0, parcelas >= 1, taxa >= 0. */
export function validateInstallments(req: InstallmentSimulationRequest): string | null {
  if (!Number.isFinite(req.totalDebt) || req.totalDebt <= 0) return 'Informe uma dívida maior que zero.';
  if (!Number.isInteger(req.installments) || req.installments < 1) return 'Informe ao menos 1 parcela.';
  if (!Number.isFinite(req.monthlyRate) || req.monthlyRate < 0) return 'Informe uma taxa maior ou igual a zero.';
  return null;
}

/**
 * AnnualSettlementCertificate.issue(...): o ano da certidao e obrigatorio
 * (Year nao-nulo e a unica exigencia da fonte — nenhuma faixa de anos consta
 * no service; faixa 2000-2100 removida na correcao R-PORTAL-02, sem fonte).
 * Na tela, ano ausente/invalido chega como NaN/nao-inteiro e e recusado.
 */
export function validateYear(ano: number): string | null {
  if (!Number.isInteger(ano)) return 'Informe o ano da certidão.';
  return null;
}

export function formatMoney(v: number): string {
  return v.toFixed(2).replace('.', ',');
}
