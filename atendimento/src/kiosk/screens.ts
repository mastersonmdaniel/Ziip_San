/**
 * Renderizacao DOM das telas da ED-40 (pacote ed-40-telas) — TypeScript
 * puro, sem framework, no padrao das telas entregues do app de campo
 * (h()/button(), rotulos associados, status em aria-live, alvos >= 48px
 * via kiosk.css).
 *
 * O que cada tela exibe — e SO isto:
 *  1. Quiosque: tipo de servico, perfil prioritario (P1.3), emissao com
 *     codigo + hora, ajuda de acessibilidade, atalho do painel. Estado
 *     de RECUSA com o quiosque desligado. Nenhuma acao autenticada.
 *  2. Painel (SG-626): chamada atual + historico (codigo, guiche, hora).
 *  3. Guiche (SG-625): fila ordenada pelo nucleo, chamar/iniciar/
 *     encerrar/ausencia/cancelar sobre o ciclo de vida entregue.
 *  4. Relatorio (RAS-26): linhas por unidade/dia do WaitingTimeReport.
 */
import {
  ACCESSIBLE_FORMAT_LABELS,
  ASSISTANCE_CHANNEL_LABELS,
  PRIORITY_CATEGORY_LABELS,
  commonProfile,
  timeOf,
  type AccessibleFormat,
  type AssistanceChannel,
  type PriorityAttendanceProfile,
  type PriorityCategory,
} from './models.ts';
import type {
  DeskController,
  KioskController,
  PanelController,
  WaitingReportController,
} from './controller.ts';
import type { TicketSnapshot } from './ports.ts';

type Attrs = Record<string, string | boolean>;

function h(tag: string, attrs: Attrs = {}, ...children: (Node | string)[]): HTMLElement {
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
  const b = h('button', { type: 'button', class: 'kiosk-btn', ...attrs }, label) as HTMLButtonElement;
  b.addEventListener('click', onClick);
  return b;
}

export type KioskScreenId = 'quiosque' | 'painel' | 'guiche' | 'relatorio';

export const SCREEN_TITLES: Record<KioskScreenId, string> = {
  quiosque: 'Quiosque — emitir senha',
  painel: 'Painel de chamada',
  guiche: 'Guichê — atendimento',
  relatorio: 'Relatório de espera (RAS-26)',
};

export interface AtendimentoControllers {
  kiosk: KioskController;
  panel: PanelController;
  desk: DeskController;
  report: WaitingReportController;
  /** Unidade exibida no painel (a do terminal/unidade selecionada na fiacao). */
  panelUnitId: string;
}

export class AtendimentoScreensView {
  private readonly root: HTMLElement;
  private readonly ctl: AtendimentoControllers;
  private readonly status: HTMLDivElement;
  private screen: KioskScreenId = 'quiosque';
  private lastFocusedScreen: KioskScreenId | null = null;

  // Estado local do formulario do quiosque.
  private selectedService = '';
  private selectedCategory: PriorityCategory = 'NONE';
  private companion = false;
  private selectedFormat: AccessibleFormat = 'NONE';
  private selectedChannel: AssistanceChannel = 'NONE';
  private issuedText: string | null = null;
  private showHelp = false;

  // Estado local do relatorio.
  private reportDe = '';
  private reportAte = '';

  constructor(root: HTMLElement, ctl: AtendimentoControllers) {
    this.root = root;
    this.ctl = ctl;
    this.status = h('div', {
      class: 'kiosk-status',
      role: 'status',
      'aria-live': 'polite',
    }) as HTMLDivElement;
  }

  private say(message: string): void {
    this.status.textContent = message;
  }

  private go(screen: KioskScreenId): void {
    this.screen = screen;
    this.render();
  }

  render(): void {
    const main = h('main', { class: 'kiosk-main' });
    const title = h('h1', { class: 'kiosk-title', tabindex: '-1' }, SCREEN_TITLES[this.screen]);
    main.append(title);
    main.append(this.navBar());
    if (this.screen === 'quiosque') main.append(this.kioskScreen());
    if (this.screen === 'painel') main.append(this.panelScreen());
    if (this.screen === 'guiche') main.append(this.deskScreen());
    if (this.screen === 'relatorio') main.append(this.reportScreen());
    main.append(this.status);
    this.root.replaceChildren(main);
    if (this.screen !== this.lastFocusedScreen) {
      title.focus();
      this.lastFocusedScreen = this.screen;
    }
  }

  private navBar(): HTMLElement {
    const nav = h('nav', { class: 'kiosk-nav', 'aria-label': 'Telas de atendimento' });
    const items: { id: KioskScreenId; label: string }[] = [
      { id: 'quiosque', label: 'Quiosque' },
      { id: 'painel', label: 'Painel' },
      { id: 'guiche', label: 'Guichê' },
      { id: 'relatorio', label: 'Relatório de espera' },
    ];
    for (const item of items) {
      nav.append(
        button(item.label, () => this.go(item.id), {
          ...(this.screen === item.id ? { 'aria-current': 'page' } : {}),
        }),
      );
    }
    return nav;
  }

  // --- Tela 1: quiosque -----------------------------------------------------

  private kioskScreen(): HTMLElement {
    const section = h('section', { class: 'kiosk-card', 'aria-label': 'Quiosque' });
    const kiosk = this.ctl.kiosk;

    if (kiosk.isRefused()) {
      // Estado de RECUSA do contrato: desligado, o terminal nao emite senha.
      section.append(h('h2', {}, 'Quiosque desligado'));
      section.append(h('p', { class: 'kiosk-refusal', role: 'alert' }, kiosk.refusalReason()));
      section.append(
        h('p', {}, 'Este terminal não emite senhas enquanto o modo quiosque estiver desligado.'),
      );
      // Correccao R2-F3: o botao NAO e condicionado a actionAllowed().
      // VIEW_CALL_PANEL da lista fechada do terminal exige enabled
      // (models.ts, espelho fiel do Java) — a condicao era sempre falsa
      // aqui e o botao era codigo morto. Ver o painel, porem, nao e uma
      // accao do terminal: o painel (SG-626) e outra tela, servida pela
      // sua rota propria sem gate de kiosk_enabled, e a barra de
      // navegacao (navBar) ja a expoe incondicionalmente em todos os
      // estados, inclusive na recusa. O atalho cumpre a mesma navegacao
      // da casca — nao emite senha nem chama a porta.
      section.append(button('Ver o painel', () => this.go('painel')));
      return section;
    }

    if (this.issuedText) {
      // Emissao: SO codigo e hora de emissao (contrato da senha impressa/exibida).
      section.append(h('h2', {}, 'Sua senha'));
      section.append(h('p', { class: 'kiosk-ticket-code' }, this.issuedText));
      section.append(
        button('Emitir outra senha', () => {
          this.issuedText = null;
          this.render();
        }),
      );
      section.append(button('Ver o painel', () => this.go('painel')));
      return section;
    }

    section.append(h('h2', {}, 'Escolha o tipo de serviço'));
    if (kiosk.serviceTypes.length === 0) {
      section.append(
        h(
          'p',
          { class: 'kiosk-pending' },
          'Catálogo de tipos de serviço [A DEFINIR] na fiação (P-ED40T-03).',
        ),
      );
    }
    const serviceGroup = h('div', { class: 'kiosk-choices', role: 'group', 'aria-label': 'Tipo de serviço' });
    for (const s of kiosk.serviceTypes) {
      serviceGroup.append(
        button(s, () => {
          this.selectedService = s;
          this.render();
        }, {
          'aria-pressed': this.selectedService === s ? 'true' : 'false',
          class: this.selectedService === s ? 'kiosk-btn kiosk-btn-selected' : 'kiosk-btn',
        }),
      );
    }
    section.append(serviceGroup);

    section.append(h('h2', {}, 'Atendimento prioritário (Lei 10.048/2000)'));
    const profileField = h('fieldset', { class: 'kiosk-fieldset' });
    profileField.append(h('legend', {}, 'Perfil de atendimento'));
    for (const [value, label] of Object.entries(PRIORITY_CATEGORY_LABELS)) {
      const id = `perfil-${value}`;
      const radio = h('input', {
        type: 'radio',
        id,
        name: 'perfil',
        value,
        ...(this.selectedCategory === value ? { checked: true } : {}),
      }) as HTMLInputElement;
      radio.addEventListener('change', () => {
        this.selectedCategory = value as PriorityCategory;
      });
      profileField.append(h('label', { for: id }, radio, ` ${label}`));
    }
    const companionId = 'acompanhante';
    const companionBox = h('input', {
      type: 'checkbox',
      id: companionId,
      ...(this.companion ? { checked: true } : {}),
    }) as HTMLInputElement;
    companionBox.addEventListener('change', () => {
      this.companion = companionBox.checked;
    });
    profileField.append(h('label', { for: companionId }, companionBox, ' Com acompanhante'));
    section.append(profileField);

    const formatLabel = h('label', { for: 'formato-acessivel' }, 'Formato acessível');
    const formatSelect = h('select', { id: 'formato-acessivel', class: 'kiosk-select' }) as HTMLSelectElement;
    for (const [value, label] of Object.entries(ACCESSIBLE_FORMAT_LABELS)) {
      const opt = h('option', { value }, label) as HTMLOptionElement;
      if (this.selectedFormat === value) opt.selected = true;
      formatSelect.append(opt);
    }
    formatSelect.addEventListener('change', () => {
      this.selectedFormat = formatSelect.value as AccessibleFormat;
    });
    section.append(formatLabel, formatSelect);

    const channelLabel = h('label', { for: 'canal-assistencia' }, 'Canal de assistência');
    const channelSelect = h('select', { id: 'canal-assistencia', class: 'kiosk-select' }) as HTMLSelectElement;
    for (const [value, label] of Object.entries(ASSISTANCE_CHANNEL_LABELS)) {
      const opt = h('option', { value }, label) as HTMLOptionElement;
      if (this.selectedChannel === value) opt.selected = true;
      channelSelect.append(opt);
    }
    channelSelect.addEventListener('change', () => {
      this.selectedChannel = channelSelect.value as AssistanceChannel;
    });
    section.append(channelLabel, channelSelect);

    section.append(
      button('Emitir senha', () => {
        void this.doIssue();
      }, { class: 'kiosk-btn kiosk-btn-primary' }),
    );
    section.append(
      button('Ajuda de acessibilidade', () => {
        this.showHelp = !this.showHelp;
        this.render();
      }),
    );
    section.append(button('Ver o painel', () => this.go('painel')));

    if (this.showHelp) section.append(this.helpSection());
    return section;
  }

  private helpSection(): HTMLElement {
    const help = this.ctl.kiosk.accessibilityHelp();
    const section = h('section', { class: 'kiosk-help', 'aria-label': 'Ajuda de acessibilidade' });
    section.append(h('h2', {}, 'Ajuda de acessibilidade'));
    if (!help) {
      section.append(h('p', {}, 'Ajuda indisponível com o quiosque desligado.'));
      return section;
    }
    section.append(h('p', {}, 'Formatos acessíveis disponíveis:'));
    const formats = h('ul', {});
    for (const f of help.formats) formats.append(h('li', {}, f.label));
    section.append(formats);
    section.append(h('p', {}, 'Canais de assistência disponíveis:'));
    const channels = h('ul', {});
    for (const c of help.channels) channels.append(h('li', {}, c.label));
    section.append(channels);
    return section;
  }

  private async doIssue(): Promise<void> {
    const profile: PriorityAttendanceProfile =
      this.selectedCategory === 'NONE'
        ? { ...commonProfile(), companionPresent: this.companion, accessibleFormat: this.selectedFormat, assistanceChannel: this.selectedChannel }
        : {
            category: this.selectedCategory,
            companionPresent: this.companion,
            accessibleFormat: this.selectedFormat,
            assistanceChannel: this.selectedChannel,
          };
    const outcome = await this.ctl.kiosk.issueTicket(this.selectedService, profile);
    if (outcome.status === 'ISSUED') {
      this.issuedText = `${outcome.view.displayCode} · emitida às ${timeOf(outcome.view.issuedAt)}`;
      this.say(`Senha ${outcome.view.displayCode} emitida.`);
    } else {
      this.say(outcome.reason);
    }
    this.render();
  }

  // --- Tela 2: painel de chamada (SG-626) ------------------------------------

  private panelScreen(): HTMLElement {
    const section = h('section', { class: 'kiosk-card kiosk-panel', 'aria-label': 'Painel de chamada' });
    section.append(
      button('Atualizar painel', () => {
        void this.ctl.panel.load(this.ctl.panelUnitId).then(() => this.render());
      }),
    );
    const current = this.ctl.panel.currentRow();
    if (!current) {
      section.append(h('p', { class: 'kiosk-panel-empty' }, 'Nenhuma senha chamada no momento.'));
    } else {
      section.append(h('p', { class: 'kiosk-panel-current-code' }, current.displayCode));
      section.append(
        h('p', { class: 'kiosk-panel-current-desk' }, `Guichê ${current.deskId} · ${current.time}`),
      );
    }
    const rows = this.ctl.panel.historyRows();
    if (rows.length > 0) {
      section.append(h('h2', {}, 'Chamadas recentes'));
      const list = h('ul', { class: 'kiosk-panel-history' });
      for (const r of rows) {
        list.append(h('li', {}, `${r.displayCode} · Guichê ${r.deskId} · ${r.time}`));
      }
      section.append(list);
    }
    return section;
  }

  // --- Tela 3: guiche (SG-625) ------------------------------------------------

  private deskScreen(): HTMLElement {
    const desk = this.ctl.desk;
    const section = h('section', { class: 'kiosk-card', 'aria-label': 'Guichê' });
    section.append(h('h2', {}, `Guichê ${desk.deskId}`));
    section.append(
      button('Chamar próxima senha', () => {
        void desk
          .callNext()
          .then((t) => {
            this.say(`Senha ${t.displayCode} chamada para o guichê ${desk.deskId}.`);
            this.render();
          })
          .catch((e: unknown) => {
            this.say(e instanceof Error ? e.message : String(e));
            this.render();
          });
      }, { class: 'kiosk-btn kiosk-btn-primary' }),
    );

    const active = desk.activeForDesk();
    if (active) section.append(this.activeTicketCard(active));

    const waiting = desk.waiting();
    section.append(h('h2', {}, `Fila de espera (${waiting.length})`));
    if (waiting.length === 0) {
      section.append(h('p', {}, 'Nenhuma senha aguardando.'));
    } else {
      const list = h('ol', { class: 'kiosk-queue' });
      for (const t of waiting) {
        const badge = t.displayCode.startsWith('P') ? ' · prioridade legal' : '';
        const item = h(
          'li',
          {},
          `${t.displayCode} · ${t.serviceType} · emitida às ${timeOf(t.issuedAt)}${badge} `,
        );
        // Correccao R2-F2: senha em espera (WAITING) pode ser cancelada
        // DIRECTAMENTE da fila — o modelo (QueueTicket.cancel) aceita
        // WAITING/CALLED; antes o unico botao de cancelar vivia no
        // cartao activo, que so recebe CALLED/IN_SERVICE, e cancelar
        // uma espera obrigava a chama-la primeiro.
        item.append(
          button('Cancelar senha', () => {
            void desk
              .cancel(t.id)
              .then(() => {
                this.say(`Senha ${t.displayCode} cancelada.`);
                this.render();
              })
              .catch((e: unknown) => {
                this.say(e instanceof Error ? e.message : String(e));
                this.render();
              });
          }),
        );
        list.append(item);
      }
      section.append(list);
    }
    return section;
  }

  private activeTicketCard(t: TicketSnapshot): HTMLElement {
    const desk = this.ctl.desk;
    const card = h('section', { class: 'kiosk-active', 'aria-label': 'Senha no guichê' });
    card.append(h('h3', {}, `Senha ${t.displayCode}`));
    card.append(
      h('p', {}, `Chamada às ${t.calledAt ? timeOf(t.calledAt) : '—'} · estado: ${t.status}`),
    );
    const run = (fn: () => Promise<TicketSnapshot>, done: string) => () => {
      void fn()
        .then(() => {
          this.say(done);
          this.render();
        })
        .catch((e: unknown) => {
          this.say(e instanceof Error ? e.message : String(e));
          this.render();
        });
    };
    if (t.status === 'CALLED') {
      card.append(button('Iniciar atendimento', run(() => desk.startService(t.id), 'Atendimento iniciado.')));
      card.append(button('Registrar ausência', run(() => desk.markNoShow(t.id), 'Ausência registrada.')));
    }
    if (t.status === 'IN_SERVICE') {
      card.append(button('Encerrar atendimento', run(() => desk.complete(t.id), 'Atendimento encerrado.')));
    }
    // Este cartao so recebe CALLED/IN_SERVICE (activeForDesk): o
    // cancelamento em WAITING vive na lista de espera (correccao
    // R2-F2); em IN_SERVICE o modelo recusa cancelar, logo sem botao.
    if (t.status === 'CALLED') {
      card.append(button('Cancelar senha', run(() => desk.cancel(t.id), 'Senha cancelada.')));
    }
    return card;
  }

  // --- Tela 4: relatorio de espera (RAS-26) -----------------------------------

  private reportScreen(): HTMLElement {
    const section = h('section', { class: 'kiosk-card', 'aria-label': 'Relatório de espera' });
    const deLabel = h('label', { for: 'rel-de' }, 'De');
    const deInput = h('input', { type: 'date', id: 'rel-de', class: 'kiosk-input', value: this.reportDe }) as HTMLInputElement;
    deInput.addEventListener('change', () => {
      this.reportDe = deInput.value;
    });
    const ateLabel = h('label', { for: 'rel-ate' }, 'Até');
    const ateInput = h('input', { type: 'date', id: 'rel-ate', class: 'kiosk-input', value: this.reportAte }) as HTMLInputElement;
    ateInput.addEventListener('change', () => {
      this.reportAte = ateInput.value;
    });
    section.append(deLabel, deInput, ateLabel, ateInput);
    section.append(
      button('Carregar relatório', () => {
        void this.ctl.report
          .load(this.reportDe, this.reportAte)
          .then(() => this.render())
          .catch((e: unknown) => {
            this.say(e instanceof Error ? e.message : String(e));
            this.render();
          });
      }),
    );

    const rows = this.ctl.report.reportRows;
    if (rows.length === 0) {
      section.append(h('p', {}, 'Nenhuma linha no período. Carregue um período para ver o relatório.'));
      return section;
    }
    const table = h('table', { class: 'kiosk-table' });
    const head = h('tr', {});
    for (const col of [
      'Unidade',
      'Dia',
      'Limite (min)',
      'Tipo do limite',
      'Iniciadas',
      'Dentro',
      'Fora',
      '% dentro',
      'Espera média (min)',
      'Espera máxima (min)',
      'Não iniciadas',
      'Encerradas sem início',
    ]) {
      head.append(h('th', { scope: 'col' }, col));
    }
    table.append(h('thead', {}, head));
    const body = h('tbody', {});
    for (const r of rows) {
      const tr = h('tr', {});
      const cells = [
        r.serviceUnitId,
        r.date,
        String(r.limitMinutes),
        r.limitKind === 'HOLIDAY_ADJACENT' ? 'Feriado prolongado (véspera/dia seguinte)' : 'Dia útil',
        String(r.startedCount),
        String(r.withinLimit),
        String(r.outsideLimit),
        r.withinPct.toFixed(2),
        r.averageWaitMinutes.toFixed(2),
        String(r.maxWaitMinutes),
        String(r.pendingStart),
        String(r.closedWithoutStart),
      ];
      for (const c of cells) tr.append(h('td', {}, c));
      body.append(tr);
    }
    table.append(body);
    section.append(table);
    return section;
  }
}
