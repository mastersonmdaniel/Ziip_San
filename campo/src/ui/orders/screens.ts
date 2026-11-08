/**
 * Renderizacao DOM das telas de OS em campo (R2-M3 / ED-20) — TypeScript puro,
 * sem framework, no padrao das telas de leitura (ui/screens.ts) e da casca
 * da ED-18 (index.html com <div id="app">). Reutiliza as classes do campo.css.
 *
 * Acessibilidade (padrao do projeto, ED-39/a11y.ts): rotulos associados
 * (label for/id), alvos de toque >= 48dp via CSS, status em aria-live,
 * titulo da tela recebe foco a cada navegacao.
 *
 * A tela NAO afirma o que nao pode provar: a conclusao e um passo local de
 * estado visivel (o servidor revalida tudo na sync); aceite, pesquisa de
 * satisfacao, materiais e GPS nao tem espelho TS e aparecem como pendencia
 * declarada, nao como controles.
 */
import type { OrderScreensController } from './controller.ts';
import type { ScannedCode, ScanChannel, VisibleState } from '../../orders/fieldOrder.ts';
import {
  ORDER_SCREEN_TITLES,
  cutReasonLabel,
  debitStateLabel,
  nextStateLabel,
  orderLabel,
  visibleStateLabel,
  type FieldOrder,
  type OrderScreenId,
} from './models.ts';

type Attrs = Record<string, string | boolean>;

function h(
  tag: string,
  attrs: Attrs = {},
  ...children: (Node | string)[]
): HTMLElement {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === false) continue;
    if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  for (const c of children) el.append(c);
  return el;
}

function button(label: string, onClick: () => void, attrs: Attrs = {}): HTMLButtonElement {
  const b = h('button', { type: 'button', class: 'campo-btn', ...attrs }, label) as HTMLButtonElement;
  b.addEventListener('click', onClick);
  return b;
}

export class OrderScreensView {
  private readonly root: HTMLElement;
  private readonly ctl: OrderScreensController;
  private status: HTMLDivElement;
  private lastFocusedScreen: OrderScreenId | null = null;

  constructor(root: HTMLElement, ctl: OrderScreensController) {
    this.root = root;
    this.ctl = ctl;
    this.status = h('div', {
      class: 'campo-status',
      role: 'status',
      'aria-live': 'polite',
    }) as HTMLDivElement;
  }

  private say(message: string): void {
    this.status.textContent = message;
  }

  render(): void {
    const screen = this.ctl.nav.current();
    const main = h('main', { class: 'campo-main' });
    const title = h('h1', { class: 'campo-title', tabindex: '-1' }, ORDER_SCREEN_TITLES[screen]);
    main.append(title);
    main.append(this.navBar());
    if (screen === 'lista') main.append(this.listScreen());
    if (screen === 'detalhe') main.append(this.detailScreen());
    main.append(this.status);
    this.root.replaceChildren(main);
    if (screen !== this.lastFocusedScreen) {
      title.focus();
      this.lastFocusedScreen = screen;
    }
  }

  private navBar(): HTMLElement {
    const nav = h('nav', { class: 'campo-nav', 'aria-label': 'Telas de ordens de serviço' });
    nav.append(
      button('Ordens de serviço', () => {
        this.ctl.nav.go('lista');
        this.render();
      }, this.ctl.nav.current() === 'lista' ? { 'aria-current': 'page' } : {}),
    );
    if (this.ctl.nav.canGoBack()) {
      nav.append(
        button('Voltar', () => {
          this.ctl.nav.back();
          this.render();
        }),
      );
    }
    return nav;
  }

  // --- Tela 1: lista de OS -------------------------------------------------
  private listScreen(): HTMLElement {
    const progress = this.ctl.progress();
    const box = h('section', { 'aria-label': 'Ordens de serviço do dia' });
    box.append(
      h('p', {}, `Progresso: ${progress.done} de ${progress.total} OS concluída(s) · ${progress.pending} pendente(s)`),
    );
    if (progress.rows.length === 0) {
      box.append(h('p', {}, 'Nenhuma ordem de serviço neste pacote.'));
      return box;
    }
    const list = h('ol', { class: 'campo-list' });
    for (const row of progress.rows) {
      const li = h('li', { class: 'campo-item' });
      li.append(
        h('span', {}, orderLabel(row.order)),
        h('span', {}, `Estado: ${visibleStateLabel(row.state)}`),
        button(row.state === 'COMPLETED' ? 'Revisar OS' : 'Abrir OS', () => {
          this.ctl.nav.openOrder(row.order.orderId);
          this.render();
        }, { 'aria-label': `Abrir ordem de serviço ${row.order.orderId}` }),
      );
      list.append(li);
    }
    box.append(list);
    return box;
  }

  // --- Tela 2: detalhe da OS ----------------------------------------------
  private detailScreen(): HTMLElement {
    const orderId = this.ctl.nav.orderId();
    const box = h('section', { 'aria-label': 'Detalhe da ordem de serviço' });
    if (!orderId) {
      box.append(h('p', {}, 'Selecione uma ordem de serviço na lista.'));
      return box;
    }
    const order = this.ctl.order(orderId);
    const state = this.ctl.stateOf(orderId);
    box.append(h('p', {}, orderLabel(order)));
    box.append(h('p', {}, `Estado: ${visibleStateLabel(state)} · emitida em ${order.issuedAt}`));
    const address = order.unitAddress?.trim();
    if (address) box.append(h('p', {}, `Endereço: ${address}`));

    box.append(this.cutSection(order));
    box.append(this.identitySection(order));
    box.append(this.photosSection(order, 'before'));
    box.append(this.photosSection(order, 'after'));
    box.append(this.advanceSection(order, state));
    box.append(this.integrationNote());
    return box;
  }

  private cutSection(order: FieldOrder): HTMLElement {
    const section = h('section', { 'aria-label': 'Revalidação de corte' });
    section.append(h('h2', {}, 'Corte'));
    const snapshot = order.cutSnapshot ?? null;
    if (!snapshot) {
      section.append(h('p', {}, 'Ordem sem pacote de corte — revalidação não se aplica.'));
      return section;
    }
    const facts = snapshot.factsAtIssue;
    section.append(h('p', {},
      `Retrato da emissão: débito ${debitStateLabel(facts.debitState)} · ` +
      `contestação ${facts.contestationOpen ? 'aberta' : 'fechada'} · ` +
      `aviso prévio ${facts.noticeGiven ? 'dado' : 'não comprovado'} · ` +
      `vedações: ${facts.prohibitions.length === 0 ? 'nenhuma' : facts.prohibitions.join(', ')}`));
    const reasons = this.ctl.cutReasons(order.orderId);
    if (reasons) {
      section.append(h('p', {},
        reasons.length === 0
          ? 'Revalidação: pode executar (nenhum motivo de cancelamento).'
          : `Revalidação: NÃO executar — ${reasons.map(cutReasonLabel).join('; ')}.`));
    }
    section.append(
      button('Revalidar corte', () => {
        void this.ctl.revalidateCut(order.orderId).then((res) => {
          if (!res.ok) {
            this.say(res.error);
          } else {
            this.say(res.reasons.length === 0
              ? 'Revalidação: pode executar.'
              : `Revalidação: não executar — ${res.reasons.map(cutReasonLabel).join('; ')}.`);
          }
          this.render();
        });
      }),
    );
    return section;
  }

  private identitySection(order: FieldOrder): HTMLElement {
    const section = h('section', { 'aria-label': 'Confirmação de identidade' });
    section.append(h('h2', {}, 'Identidade'));
    const expected = order.expectedIdentity ?? null;
    if (!expected) {
      section.append(h('p', {}, 'Ordem sem identidade esperada no pacote — confirmação indisponível.'));
      return section;
    }
    section.append(h('p', {},
      `Esperado: ${expected.target} · código ${expected.code} · unidade ${expected.unitId}`));
    if (this.ctl.isIdentityConfirmed(order.orderId)) {
      section.append(h('p', {}, 'Identidade confirmada nesta sessão.'));
    }
    const codeId = 'campo-os-codigo';
    const codeInput = h('input', {
      id: codeId, class: 'campo-input', value: '', autocomplete: 'off',
      placeholder: 'Código lido (QR, barras ou NFC)',
    }) as HTMLInputElement;
    section.append(h('label', { for: codeId }, 'Código escaneado'), codeInput);
    const channelId = 'campo-os-canal';
    const channelSelect = h('select', { id: channelId, class: 'campo-input' }) as HTMLSelectElement;
    for (const channel of ['QR', 'BARCODE', 'NFC'] as const) {
      channelSelect.append(h('option', { value: channel }, channel));
    }
    section.append(h('label', { for: channelId }, 'Canal'), channelSelect);
    section.append(
      button('Confirmar identidade', () => {
        const scanned: ScannedCode = {
          unitId: null,
          target: expected.target,
          code: codeInput.value.trim(),
          channel: channelSelect.value as ScanChannel,
        };
        const res = this.ctl.confirmIdentity(order.orderId, scanned);
        this.say(res.ok ? 'Identidade confirmada.' : res.error);
        this.render();
      }),
    );
    return section;
  }

  private photosSection(order: FieldOrder, kind: 'before' | 'after'): HTMLElement {
    const section = h('section', {
      'aria-label': kind === 'before' ? 'Foto de antes' : 'Foto de depois',
    });
    section.append(h('h2', {}, kind === 'before' ? 'Foto de antes' : 'Foto de depois'));
    const refs = kind === 'before'
      ? this.ctl.beforePhotoRefs(order.orderId)
      : this.ctl.afterPhotoRefs(order.orderId);
    const inputId = kind === 'before' ? 'campo-os-foto-antes' : 'campo-os-foto-depois';
    const input = h('input', {
      id: inputId, class: 'campo-input', value: '', placeholder: 'URI local da foto',
    }) as HTMLInputElement;
    section.append(h('label', { for: inputId }, 'URI local da foto'), input);
    for (const ref of refs) {
      section.append(
        h('p', {}, `Foto anexada: ${ref}`),
        button(`Remover foto ${ref}`, () => {
          if (kind === 'before') this.ctl.removeBeforePhoto(order.orderId, ref);
          else this.ctl.removeAfterPhoto(order.orderId, ref);
          this.render();
        }),
      );
    }
    section.append(
      button(kind === 'before' ? 'Anexar foto de antes' : 'Anexar foto de depois', () => {
        const ok = kind === 'before'
          ? this.ctl.addBeforePhoto(order.orderId, input.value)
          : this.ctl.addAfterPhoto(order.orderId, input.value);
        this.say(ok ? 'Foto anexada.' : 'Informe a URI local da foto para anexar.');
        this.render();
      }),
    );
    section.append(h('p', {},
      'A captura pela câmera ainda não tem contrato no app (pendência P-07); ' +
      'informe a URI local. A varredura e o desfoque são validados pelo servidor na sincronização.'));
    return section;
  }

  private advanceSection(order: FieldOrder, state: VisibleState): HTMLElement {
    const section = h('section', { 'aria-label': 'Avanço da execução' });
    const next = nextStateLabel(state);
    if (next === null) {
      section.append(h('p', {}, 'OS concluída nesta sessão — o servidor revalida tudo na sincronização.'));
      return section;
    }
    section.append(
      button(next, () => {
        const res = this.ctl.advance(order.orderId);
        this.say(res.ok ? `Estado atualizado: ${visibleStateLabel(this.ctl.stateOf(order.orderId))}.` : res.error);
        this.render();
      }),
    );
    return section;
  }

  private integrationNote(): HTMLElement {
    const section = h('section', { 'aria-label': 'Integração ED-20' });
    section.append(h('p', {},
      'Aceite por assinatura/código, pesquisa de satisfação, materiais e trilha GPS ' +
      'vivem só no núcleo Java da ED-20, sem espelho TS — os controles chegam com ' +
      'a integração das portas (P-ED20-02). A conclusão aqui é o passo local de ' +
      'estado visível; o servidor revalida tudo na sincronização.'));
    return section;
  }
}
