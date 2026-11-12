/**
 * Telas do portal do consumidor (DOM puro, mesmo padrao do cliente da
 * ED-39: TypeScript sem framework, montadas na casca <main id="app">).
 * Acessibilidade no padrao do a11y.ts da ED-39: rotulo associado a cada
 * campo, regiao aria-live para o estado, alvos de toque >= 48px via CSS.
 * Estados de toda tela assincrona: carregando / erro / vazio — o texto de
 * erro exibido e o da resposta (message do servidor), nunca mensagem de
 * negocio inventada nesta camada.
 */
import { PortalClient, type CentralizerScreenDeps } from './transport.ts';
import {
  REPORT_CATEGORIES,
  formatMoney,
  validateBillSimulation,
  validateCentralizerReference,
  validateCentralizerSession,
  validateGuest,
  validateInstallments,
  validateReport,
  validateYear,
} from './models.ts';

type El = HTMLElement;

function h(tag: string, text?: string): El {
  const el = document.createElement(tag) as El;
  if (text !== undefined) el.textContent = text;
  return el;
}

function field(labelText: string, id: string, type = 'text'): { wrap: El; input: HTMLInputElement } {
  const wrap = h('div');
  wrap.setAttribute('class', 'portal-field');
  const label = h('label', labelText);
  label.setAttribute('for', id);
  const input = document.createElement('input') as HTMLInputElement;
  input.setAttribute('id', id);
  input.setAttribute('type', type);
  wrap.appendChild(label);
  wrap.appendChild(input);
  return { wrap, input };
}

function statusRegion(): El {
  const s = h('p');
  s.setAttribute('role', 'status');
  s.setAttribute('aria-live', 'polite');
  s.setAttribute('class', 'portal-status');
  return s;
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

async function run<T>(
  status: El, emptyText: string | null, work: () => Promise<T>, render: (v: T) => void,
): Promise<void> {
  status.textContent = 'Carregando…';
  try {
    const v = await work();
    const isEmpty = Array.isArray(v) && v.length === 0;
    status.textContent = isEmpty && emptyText ? emptyText : '';
    if (!isEmpty) render(v);
  } catch (err) {
    status.textContent = `Erro: ${errorMessage(err)}`;
  }
}

function section(root: El, title: string): El {
  const s = h('section');
  const t = h('h2', title);
  t.setAttribute('tabindex', '-1');
  s.appendChild(t);
  root.appendChild(s);
  return s;
}

function button(text: string, onClick: () => void): El {
  const b = h('button', text);
  b.setAttribute('type', 'button');
  b.setAttribute('class', 'portal-action');
  b.addEventListener('click', onClick);
  return b;
}

// ---- Tela 1: acesso do visitante -------------------------------------------

export function renderGuest(root: El, client: PortalClient): void {
  const s = section(root, 'Fatura sem cadastro');
  const code = field('Código de vinculação', 'guest-code');
  const suffix = field('Final do documento', 'guest-suffix');
  const status = statusRegion();
  const out = h('div');
  s.appendChild(code.wrap); s.appendChild(suffix.wrap); s.appendChild(status); s.appendChild(out);
  s.appendChild(button('Consultar fatura', () => {
    const req = { linkingCode: code.input.value, documentSuffix: suffix.input.value };
    const invalid = validateGuest(req);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    void run(status, null, () => client.guestInvoice(req), (v) => {
      // GuestInvoiceView: somente os 5 campos do contrato.
      out.appendChild(h('p', `Valor: R$ ${formatMoney(v.amount)}`));
      out.appendChild(h('p', `Vencimento: ${v.dueDate}`));
      out.appendChild(h('p', `Mês de referência: ${v.referenceMonth}`));
      out.appendChild(h('p', `Linha digitável: ${v.digitableLine}`));
      out.appendChild(h('p', `Pix copia e cola: ${v.pixCopyPaste}`));
    });
  }));
}

// ---- Tela 2: minhas faturas --------------------------------------------------

export function renderBills(root: El, client: PortalClient): void {
  const s = section(root, 'Minhas faturas');
  const unit = field('Unidade', 'bills-unit');
  const status = statusRegion();
  const list = h('ul');
  s.appendChild(unit.wrap); s.appendChild(status); s.appendChild(list);
  s.appendChild(button('Listar faturas', () => {
    list.textContent = '';
    void run(status, 'Nenhuma fatura encontrada.', () => client.listBills(unit.input.value), (bills) => {
      for (const b of bills) {
        list.appendChild(h('li',
          `${b.reference} — vencimento ${b.dueDate} — R$ ${formatMoney(b.amount)} — ${b.status}`));
      }
    });
  }));
}

// ---- Tela 3: segunda via -----------------------------------------------------

export function renderSecondCopy(root: El, client: PortalClient): void {
  const s = section(root, 'Segunda via');
  const bill = field('Identificador da fatura', 'sc-bill');
  const unit = field('Identificador da unidade', 'sc-unit');
  const status = statusRegion();
  const out = h('div');
  s.appendChild(bill.wrap); s.appendChild(unit.wrap); s.appendChild(status); s.appendChild(out);
  s.appendChild(button('Segunda via (documento)', () => {
    out.textContent = '';
    void run(status, null, () => client.secondCopyBff(bill.input.value), (ref) => {
      out.appendChild(h('p', `Documento: ${ref}`));
    });
  }));
  s.appendChild(button('Pix copia e cola', () => {
    out.textContent = '';
    void run(status, null, () => client.secondCopyPix(unit.input.value), (v) => {
      out.appendChild(h('p', `Pix copia e cola: ${v.pixCopyPaste}`));
    });
  }));
  s.appendChild(button('Baixar PDF da segunda via', () => {
    void run(status, null, () => client.secondCopyPdf(unit.input.value), (pdf) => {
      const url = URL.createObjectURL(new Blob([pdf], { type: 'application/pdf' }));
      const a = h('a', 'Abrir PDF da segunda via');
      // Alvo >= 48px via .portal-action de portal.css (correcao R-PORTAL-04).
      a.setAttribute('class', 'portal-action');
      a.setAttribute('href', url);
      out.textContent = '';
      out.appendChild(a);
    });
  }));
}

// ---- Tela 4: explicacao da fatura ---------------------------------------------

export function renderExplanation(root: El, client: PortalClient): void {
  const s = section(root, 'Explicação da fatura');
  const bill = field('Identificador da fatura', 'ex-bill');
  const status = statusRegion();
  const out = h('div');
  s.appendChild(bill.wrap); s.appendChild(status); s.appendChild(out);
  s.appendChild(button('Ver explicação', () => {
    out.textContent = '';
    void run(status, null, () => client.billExplanation(bill.input.value), (v) => {
      for (const f of v.factors) out.appendChild(h('p', f.text));
      out.appendChild(h('p', v.fullText));
    });
  }));
}

// ---- Tela 5: simulacao ----------------------------------------------------------

export function renderSimulation(root: El, client: PortalClient): void {
  const s = section(root, 'Simulação');
  // Declaracao do contrato da ED-38: simulacao NAO cria acordo (acordo e P4.1).
  s.appendChild(h('p', 'Simulação: não cria acordo.'));
  const status = statusRegion();
  const out = h('div');

  const cons = field('Consumo (m³)', 'sim-cons', 'number');
  const minFee = field('Taxa mínima vigente', 'sim-min', 'number');
  const bandUpTo = field('Faixa: até (m³)', 'sim-band-upto', 'number');
  const bandPrice = field('Faixa: preço por m³', 'sim-band-price', 'number');
  const sewage = field('Esgoto (% sobre a água)', 'sim-sewage', 'number');
  s.appendChild(cons.wrap); s.appendChild(minFee.wrap); s.appendChild(bandUpTo.wrap);
  s.appendChild(bandPrice.wrap); s.appendChild(sewage.wrap);
  s.appendChild(button('Simular fatura', () => {
    const req = {
      consumptionM3: Number(cons.input.value),
      tariff: {
        minimumFee: Number(minFee.input.value),
        bands: [{ upToM3: Number(bandUpTo.input.value), pricePerM3: Number(bandPrice.input.value) }],
        sewagePercent: Number(sewage.input.value),
      },
    };
    const invalid = validateBillSimulation(req);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    void run(status, null, () => client.simulateBill(req), (v) => {
      out.appendChild(h('p', `Água: R$ ${formatMoney(v.waterAmount)}`));
      out.appendChild(h('p', `Esgoto: R$ ${formatMoney(v.sewageAmount)}`));
      out.appendChild(h('p', `Total simulado: R$ ${formatMoney(v.totalAmount)}`));
    });
  }));

  const debt = field('Dívida total', 'sim-debt', 'number');
  const inst = field('Número de parcelas', 'sim-inst', 'number');
  const rate = field('Taxa mensal (%)', 'sim-rate', 'number');
  s.appendChild(debt.wrap); s.appendChild(inst.wrap); s.appendChild(rate.wrap);
  s.appendChild(button('Simular parcelamento', () => {
    const req = {
      totalDebt: Number(debt.input.value),
      installments: Number(inst.input.value),
      monthlyRate: Number(rate.input.value),
    };
    const invalid = validateInstallments(req);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    void run(status, null, () => client.simulateInstallments(req), (v) => {
      v.amounts.forEach((a, i) => out.appendChild(h('p', `Parcela ${i + 1}: R$ ${formatMoney(a)}`)));
    });
  }));
  s.appendChild(status); s.appendChild(out);
}

// ---- Tela 6: certidao de quitacao anual ------------------------------------------

export function renderCertificate(root: El, client: PortalClient): void {
  const s = section(root, 'Certidão de quitação anual');
  const year = field('Ano', 'cert-year', 'number');
  const status = statusRegion();
  const out = h('div');
  s.appendChild(year.wrap); s.appendChild(status); s.appendChild(out);
  s.appendChild(button('Emitir certidão', () => {
    const ano = Number(year.input.value);
    const invalid = validateYear(ano);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    void run(status, null, () => client.annualCertificate(ano), (v) => {
      out.appendChild(h('p', `Documento: ${v.documentNumber}`));
      out.appendChild(h('p', `Tipo: ${v.documentType}`));
      out.appendChild(h('p', `Emitido em: ${v.issuedOn}`));
      out.appendChild(h('p', `Nível de assinatura: ${v.signatureLevel}`));
      out.appendChild(h('p', `Código verificador: ${v.verificationCode}`));
      out.appendChild(h('p', `Validação: ${v.qrPayload}`));
    });
  }));
}

// ---- Tela 7: validar documento -----------------------------------------------------

export function renderValidate(root: El, client: PortalClient): void {
  const s = section(root, 'Validar documento');
  const tipo = field('Tipo do documento', 'val-tipo');
  const numero = field('Número do documento', 'val-numero');
  const codigo = field('Código verificador', 'val-codigo');
  const status = statusRegion();
  const out = h('div');
  s.appendChild(tipo.wrap); s.appendChild(numero.wrap); s.appendChild(codigo.wrap);
  s.appendChild(status); s.appendChild(out);
  s.appendChild(button('Validar', () => {
    out.textContent = '';
    void run(status, null,
      () => client.validateDocument(tipo.input.value, numero.input.value, codigo.input.value),
      (v) => { out.appendChild(h('p', v.valid ? 'Documento válido.' : 'Documento não confere.')); });
  }));
}

// ---- Tela 8: relato anonimo -----------------------------------------------------------

export function renderReport(root: El, client: PortalClient): void {
  const s = section(root, 'Relato anônimo');
  const wrap = h('div');
  wrap.setAttribute('class', 'portal-field');
  const label = h('label', 'Categoria');
  label.setAttribute('for', 'rep-category');
  const select = document.createElement('select') as HTMLSelectElement;
  select.setAttribute('id', 'rep-category');
  for (const c of REPORT_CATEGORIES) {
    const o = h('option', c);
    o.setAttribute('value', c);
    select.appendChild(o);
  }
  wrap.appendChild(label); wrap.appendChild(select);
  const loc = field('Localização', 'rep-location');
  const photo = field('Foto (referência) — obrigatória', 'rep-photo');
  const contact = field('Contato (opcional)', 'rep-contact');
  const desc = field('Descrição', 'rep-desc');
  const status = statusRegion();
  const out = h('div');
  s.appendChild(wrap); s.appendChild(loc.wrap); s.appendChild(photo.wrap);
  s.appendChild(contact.wrap); s.appendChild(desc.wrap); s.appendChild(status); s.appendChild(out);
  s.appendChild(button('Enviar relato', () => {
    const req = {
      category: (select as unknown as { value: string }).value as (typeof REPORT_CATEGORIES)[number],
      locationText: loc.input.value,
      photoRef: photo.input.value,
      contactOrNull: contact.input.value.trim() ? contact.input.value : null,
      description: desc.input.value,
    };
    const invalid = validateReport(req);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    void run(status, null, () => client.submitReport(req), (v) => {
      out.appendChild(h('p', `Protocolo: ${v.protocolId}`));
      out.appendChild(h('p', v.groupedIntoExisting
        ? 'Relato agrupado a um protocolo existente.'
        : 'Protocolo novo criado.'));
    });
  }));
}

// ---- Tela 9: abastecimento --------------------------------------------------------------

export function renderOutage(root: El, client: PortalClient): void {
  const s = section(root, 'Abastecimento e interrupções');
  const addr = field('Endereço', 'out-addr');
  const status = statusRegion();
  const list = h('ul');
  s.appendChild(addr.wrap); s.appendChild(status); s.appendChild(list);
  s.appendChild(button('Consultar', () => {
    list.textContent = '';
    void run(status, 'Nenhuma interrupção encontrada.', () => client.outageStatus(addr.input.value), (entries) => {
      for (const e of entries) {
        const base = `${e.locality} — ${e.sector} — ${e.route} — início ${e.start} — retorno previsto ${e.expectedReturn} — ${e.phase}`;
        list.appendChild(h('li', e.reasonOrNull ? `${base} — ${e.reasonOrNull}` : base));
      }
    });
  }));
}

// ---- Tela 10: agendamento ------------------------------------------------------------------

export function renderScheduling(root: El, client: PortalClient): void {
  const s = section(root, 'Agendamento');
  const status = statusRegion();
  const list = h('ul');
  const osId = field('Ordem de serviço', 'sch-os');
  const win = field('Janela escolhida', 'sch-window');
  const start = field('Início da janela', 'sch-start');
  const out = h('div');
  s.appendChild(status); s.appendChild(list); s.appendChild(osId.wrap);
  s.appendChild(win.wrap); s.appendChild(start.wrap); s.appendChild(out);
  s.appendChild(button('Ver janelas', () => {
    list.textContent = '';
    void run(status, 'Nenhuma janela disponível.', () => client.schedulingWindows(), (windows) => {
      for (const w of windows) {
        list.appendChild(h('li', `${w.windowId} — início ${w.start} — ${w.freeSlots} vagas`));
      }
    });
  }));
  s.appendChild(button('Agendar', () => {
    out.textContent = '';
    const req = { serviceOrderId: osId.input.value, windowId: win.input.value, start: start.input.value };
    void run(status, null, () => client.book(req), (ref) => {
      out.appendChild(h('p', `Agendamento: ${ref}`));
    });
  }));
}

// ---- Tela 11: centralizador (ED-22, CentralizerViewService) ----------------

export function renderCentralizer(root: El, deps?: CentralizerScreenDeps): void {
  const s = section(root, 'Centralizador e grande cliente');
  const status = statusRegion();
  const list = h('ul');
  const ref = field('Competência (AAAA-MM)', 'cent-reference');
  const out = h('div');
  s.appendChild(status); s.appendChild(list); s.appendChild(ref.wrap); s.appendChild(out);
  if (!deps) {
    // Sem porta entregue pela fiacao: estado honesto da pendencia
    // P-PORTAL-01 — nenhuma unidade/fatura é exibida sem a porta.
    s.appendChild(h('p',
      'A fiação ainda não entregou a porta do centralizador: rota HTTP [A DEFINIR] '
      + '(pendência P-PORTAL-01 — ecrã e porta tipada entregues).'));
    return;
  }
  const { port, session } = deps;
  const args = (): [string, string, string] => [
    session.tenantId, session.consumerSubject, session.centralizerCustomerId,
  ];
  const blocked = (): boolean => {
    const invalid = validateCentralizerSession(session);
    if (invalid) { status.textContent = invalid; return true; }
    return false;
  };
  s.appendChild(button('Listar unidades vinculadas', () => {
    if (blocked()) return;
    list.textContent = '';
    void run(status, 'Nenhuma unidade vinculada.', () => port.units(...args(), session.channel), (units) => {
      for (const u of units) {
        list.appendChild(h('li',
          `Matrícula ${u.registration} — unidade ${u.unitId} — em aberto R$ ${formatMoney(u.openAmount)}`));
      }
    });
  }));
  s.appendChild(button('Exportar unidades (CSV)', () => {
    if (blocked()) return;
    out.textContent = '';
    void run(status, null, () => port.exportUnitsCsv(...args(), session.channel), (csv) => {
      // O CSV exibido/baixado e EXACTAMENTE o que a porta devolve
      // (CentralizerViewService.exportUnitsCsv) — a tela nao o remonta.
      const pre = h('pre', csv);
      pre.setAttribute('class', 'portal-csv');
      out.appendChild(pre);
      if (typeof URL.createObjectURL === 'function') {
        const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
        const a = h('a', 'Baixar CSV das unidades');
        a.setAttribute('class', 'portal-action');
        a.setAttribute('href', url);
        a.setAttribute('download', 'unidades-centralizador.csv');
        out.appendChild(a);
      }
    });
  }));
  s.appendChild(button('Ver fatura agrupada', () => {
    if (blocked()) return;
    const reference = ref.input.value.trim();
    const invalid = validateCentralizerReference(reference);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    void run(status, null,
      () => port.groupedBill(...args(), reference, session.channel),
      (v) => {
        out.appendChild(h('p', `Código: ${v.code}`));
        out.appendChild(h('p', `Competência: ${v.reference}`));
        out.appendChild(h('p', `Total: R$ ${formatMoney(v.total)}`));
        out.appendChild(h('p', `Unidades na fatura agrupada: ${v.memberCount}`));
      });
  }));
}
