/**
 * Renderizacao DOM das telas do app de campo (LOTE 3 / M1) — TypeScript puro,
 * sem framework, no padrao da casca da ED-18 (index.html com <div id="app">).
 *
 * Acessibilidade (padrao do projeto, ED-39/a11y.ts): rotulos associados
 * (label for/id), alvos de toque >= 48dp via CSS (campo.css), status em
 * aria-live, titulo da tela recebe foco a cada navegacao.
 *
 * Nenhuma tela recalcula conta: a emissao exibe o FieldBillResultDto que a
 * fachada da ED-19 devolveu. Nenhum campo alem dos DTOs reais e exibido —
 * RouteTask nao tem endereco (pendencia P-01), entao a identificacao da
 * unidade usa unitId/installationId, que sao o que o contrato expoe.
 */
import type { FieldScreensController } from './controller.ts';
import {
  SCREEN_TITLES,
  billDisplayRows,
  directiveLabel,
  outcomeDisplay,
  printerHealthDisplay,
  syncView,
  taskLabel,
  type ScreenId,
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

export class FieldScreensView {
  private readonly root: HTMLElement;
  private readonly ctl: FieldScreensController;
  private status: HTMLDivElement;
  /** Ultima tela cujo titulo ja recebeu foco (foco SO na navegacao). */
  private lastFocusedScreen: ScreenId | null = null;

  constructor(root: HTMLElement, ctl: FieldScreensController) {
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
    const title = h('h1', { class: 'campo-title', tabindex: '-1' }, SCREEN_TITLES[screen]);
    main.append(title);
    main.append(this.navBar());
    if (screen === 'rota') main.append(this.routeScreen());
    if (screen === 'leitura') main.append(this.readingScreen());
    if (screen === 'emissao') main.append(this.emissionScreen());
    if (screen === 'retidas') main.append(this.heldScreen());
    if (screen === 'sincronizacao') main.append(this.syncScreen());
    main.append(this.status);
    this.root.replaceChildren(main);
    // A11y (correcao D-04): o titulo recebe foco SOMENTE quando a tela
    // muda (navegacao). Re-renderizar a MESMA tela apos uma acao (OCR,
    // anexar foto, confirmar, emitir, sincronizar) NAO rouba o foco do
    // campo/botao onde o leiturista esta — focar a cada renderizacao
    // quebrava a navegacao por teclado/leitor de tela no meio da tarefa.
    if (screen !== this.lastFocusedScreen) {
      title.focus();
      this.lastFocusedScreen = screen;
    }
  }

  private navBar(): HTMLElement {
    const nav = h('nav', { class: 'campo-nav', 'aria-label': 'Telas do app de campo' });
    const items: { id: ScreenId; label: string }[] = [
      { id: 'rota', label: 'Rota' },
      { id: 'retidas', label: `Retenções (${this.ctl.held.count()})` },
      { id: 'sincronizacao', label: 'Sincronização' },
    ];
    for (const item of items) {
      nav.append(
        button(item.label, () => {
          this.ctl.nav.go(item.id);
          this.render();
        }, this.ctl.nav.current() === item.id ? { 'aria-current': 'page' } : {}),
      );
    }
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

  // --- Tela 1: rota / tarefas do dia --------------------------------------
  private routeScreen(): HTMLElement {
    const pkg = this.ctl.pkg;
    const progress = this.ctl.progress();
    const box = h('section', { 'aria-label': 'Tarefas da rota' });
    box.append(
      h('p', {}, `Rota ${pkg.routeId} · referência ${pkg.reference} · pacote baixado em ${pkg.downloadedAt}`),
      h('p', {}, `Progresso: ${progress.done} de ${progress.total} leituras feitas · ${progress.pending} pendente(s)`),
    );
    const list = h('ol', { class: 'campo-list' });
    for (const row of progress.rows) {
      const li = h('li', { class: 'campo-item' });
      li.append(
        h('span', {}, `${row.task.sequence}. ${taskLabel(row.task)}`),
        h('span', {}, row.state === 'LIDA' ? 'Leitura registrada' : 'Pendente'),
        h('span', {}, `Leitura anterior: ${row.task.previousReading ?? '—'} · mostrador de ${row.task.meterDigits} dígitos`),
        button(row.state === 'LIDA' ? 'Revisar leitura' : 'Fazer leitura', () => {
          this.ctl.nav.openTask(row.task.taskId);
          this.render();
        }, { 'aria-label': `Abrir leitura da unidade ${row.task.unitId}` }),
      );
      list.append(li);
    }
    box.append(list);
    return box;
  }

  // --- Tela 2: leitura de uma unidade --------------------------------------
  private readingScreen(): HTMLElement {
    const taskId = this.ctl.nav.taskId();
    const box = h('section', { 'aria-label': 'Leitura da unidade' });
    if (!taskId) {
      box.append(h('p', {}, 'Selecione uma unidade na rota.'));
      return box;
    }
    const task = this.ctl.task(taskId);
    const draft = this.ctl.draftFor(taskId);
    box.append(h('p', {}, taskLabel(task)));
    box.append(h('p', {}, `Referência ${task.reference} · leitura anterior: ${task.previousReading ?? '—'}`));

    const readingId = 'campo-leitura-atual';
    const input = h('input', {
      id: readingId,
      class: 'campo-input',
      inputmode: 'numeric',
      autocomplete: 'off',
      value: draft.readingText,
    }) as HTMLInputElement;
    box.append(h('label', { for: readingId }, 'Leitura atual do mostrador'), input);

    const occId = 'campo-ocorrencia';
    const select = h('select', { id: occId, class: 'campo-input' }) as HTMLSelectElement;
    select.append(h('option', { value: '' }, 'Sem ocorrência (leitura normal)'));
    for (const occ of this.ctl.pkg.occurrences) {
      const notes = `${occ.requiresReading ? ' · exige leitura' : ''}${occ.requiresPhoto ? ' · exige foto' : ''}`;
      const opt = h('option', { value: occ.code }, `${occ.code}${notes}`);
      if (draft.occurrenceCode === occ.code) opt.setAttribute('selected', '');
      select.append(opt);
    }
    box.append(h('label', { for: occId }, 'Ocorrência'), select);

    // Foto da ocorrencia (correcao D-01): photoRefs sao URIs locais no
    // contrato real (buildSyncPayload da ED-18) — a tela oferece o campo
    // manual de URI, o mesmo padrao do visor/OCR abaixo. SEM este campo,
    // uma ocorrencia com requiresPhoto era impossivel de confirmar: a
    // validacao exige photoRefs e a tela nao tinha como preenche-lo (a
    // regra de validacao NAO foi afrouxada). Captura por camera nao tem
    // contrato TS e segue pendencia (P-07) — aqui entra a referencia da
    // foto que o leiturista ja tem no aparelho.
    const occPhotoId = 'campo-foto-ocorrencia';
    const occPhotoInput = h('input', {
      id: occPhotoId,
      class: 'campo-input',
      value: '',
      placeholder: 'URI local da foto da ocorrência',
    }) as HTMLInputElement;
    box.append(h('label', { for: occPhotoId }, 'Foto da ocorrência (URI local)'), occPhotoInput);
    box.append(
      h('p', {},
        'Ocorrências marcadas "exige foto" só confirmam com ao menos uma foto anexada. ' +
        'A captura pela câmera ainda não tem contrato no app (pendência P-07); informe a URI local da foto.'),
    );
    for (const ref of draft.photoRefs) {
      box.append(
        h('p', {}, `Foto anexada: ${ref}`),
        button(`Remover foto ${ref}`, () => {
          draft.readingText = input.value;
          draft.occurrenceCode = select.value === '' ? null : select.value;
          this.ctl.removeOccurrencePhoto(taskId, ref);
          this.render();
        }),
      );
    }
    box.append(
      button('Anexar foto da ocorrência', () => {
        draft.readingText = input.value;
        draft.occurrenceCode = select.value === '' ? null : select.value;
        if (!this.ctl.addOccurrencePhoto(taskId, occPhotoInput.value)) {
          this.say('Informe a URI local da foto da ocorrência para anexar.');
        } else {
          this.say('Foto da ocorrência anexada.');
        }
        this.render();
      }),
    );

    if (this.depsOcrAvailable()) {
      const photoId = 'campo-foto-ocr';
      const photoInput = h('input', {
        id: photoId,
        class: 'campo-input',
        value: '',
        placeholder: 'URI local da foto do visor',
      }) as HTMLInputElement;
      box.append(h('label', { for: photoId }, 'Foto do visor (OCR no aparelho)'), photoInput);
      box.append(
        button('Reconhecer dígitos (OCR)', () => {
          draft.readingText = input.value;
          void this.ctl.runOcr(taskId, photoInput.value).then((value) => {
            this.say(
              value === null
                ? 'OCR não reconheceu dígitos nesta foto.'
                : `OCR leu ${value}. Veredito: ${this.ctl.ocrVerdict(taskId)}.`,
            );
            this.render();
          });
        }),
      );
      if (draft.ocrValue !== null) {
        box.append(h('p', {}, `Valor OCR: ${draft.ocrValue} · veredito: ${this.ctl.ocrVerdict(taskId)}`));
      }
    }

    box.append(
      button('Confirmar leitura', () => {
        draft.readingText = input.value;
        draft.occurrenceCode = select.value === '' ? null : select.value;
        // URI digitada e ainda nao anexada entra pelo MESMO caminho do
        // botao "Anexar foto da ocorrencia" (controller), nunca por
        // atribuicao direta a photoRefs fora da camada de tela.
        if (occPhotoInput.value.trim() !== '') {
          this.ctl.addOccurrencePhoto(taskId, occPhotoInput.value);
        }
        void this.ctl.submitReading(taskId, draft).then((res) => {
          if (!res.ok) {
            this.say(res.error);
            this.render();
            return;
          }
          this.say(
            res.persisted
              ? 'Leitura registrada na fila local.'
              : 'Leitura registrada apenas nesta sessão — gravação local pendente de integração (P-04).',
          );
          this.ctl.nav.go('emissao', taskId);
          this.render();
        });
      }),
    );
    return box;
  }

  private depsOcrAvailable(): boolean {
    // O apoio OCR so aparece quando o motor on-device foi injetado na
    // integracao; sem ele, nenhum controle falso e exibido.
    return this.ctl.hasOcrEngine();
  }

  // --- Tela 3: emissao / impressao -----------------------------------------
  private emissionScreen(): HTMLElement {
    const taskId = this.ctl.nav.taskId();
    const box = h('section', { 'aria-label': 'Emissão da conta' });
    if (!taskId) {
      box.append(h('p', {}, 'Selecione uma unidade na rota.'));
      return box;
    }
    box.append(h('p', {}, taskLabel(this.ctl.task(taskId))));
    const bill = this.ctl.billFor(taskId);
    const outcome = this.ctl.outcomeFor(taskId);
    if (outcome) {
      const d = outcomeDisplay(outcome);
      box.append(h('p', {}, `${d.title} — ${d.detail}`));
    }
    if (bill) {
      const table = h('table', { class: 'campo-table', 'aria-label': 'Conta emitida em campo' });
      const body = h('tbody');
      for (const row of billDisplayRows(bill)) {
        body.append(h('tr', {}, h('th', { scope: 'row' }, row.label), h('td', {}, row.value)));
      }
      table.append(body);
      box.append(table);
      box.append(
        button('Reimprimir conta', () => {
          void this.ctl.reprint(taskId).then((ok) => {
            this.say(ok ? 'Reimpressão enviada à impressora.' : 'Conta não está retida para reimpressão.');
            this.render();
          });
        }),
      );
    }
    box.append(
      button('Emitir e imprimir conta', () => {
        void this.ctl.emit(taskId).then((res) => {
          if (res.status === 'DONE') {
            const d = outcomeDisplay(res.outcome);
            this.say(`${d.title}. ${d.detail}`);
          } else {
            this.say(res.reason);
          }
          this.render();
        });
      }),
    );
    const retained = this.ctl.retainedBills();
    box.append(
      h('p', {}, `Contas impressas aguardando envio: ${retained.length}`),
    );
    // Saude da impressora (correcao D-02): exibe SOMENTE o que
    // ThermalPrinterBridge.health() devolve (cabecote/bateria, ED-19).
    // Sem a ponte, a tela declara a indisponibilidade em vez de omitir.
    const healthBox = h('section', { 'aria-label': 'Saúde da impressora' });
    healthBox.append(h('h2', {}, 'Impressora'));
    for (const line of printerHealthDisplay(this.ctl.printerHealthState())) {
      healthBox.append(h('p', {}, line));
    }
    if (this.ctl.hasPrinter()) {
      healthBox.append(
        button('Verificar saúde da impressora', () => {
          void this.ctl.refreshPrinterHealth().then((state) => {
            this.say(
              state.status === 'READY'
                ? 'Saúde da impressora atualizada.'
                : 'Não foi possível consultar a saúde da impressora.',
            );
            this.render();
          });
        }),
      );
    }
    box.append(healthBox);
    return box;
  }

  // --- Tela 4: fila de retencoes -------------------------------------------
  private heldScreen(): HTMLElement {
    const box = h('section', { 'aria-label': 'Fila de retenções de campo' });
    const entries = this.ctl.held.list();
    if (entries.length === 0) {
      box.append(h('p', {}, 'Nenhuma retenção de campo nesta sessão.'));
    } else {
      const list = h('ul', { class: 'campo-list' });
      for (const e of entries) {
        list.append(
          h('li', { class: 'campo-item' },
            h('span', {}, `${e.title} · tarefa ${e.taskId}`),
            h('span', {}, e.detail),
            button('Abrir tarefa', () => {
              this.ctl.nav.openTask(e.taskId);
              this.render();
            }),
          ),
        );
      }
      box.append(list);
    }
    box.append(
      h('p', {},
        'Divergências de valor entre campo e servidor são tratadas na fila do servidor (P2.20/ED-19) ' +
        'e não aparecem nesta tela: o contrato de campo não expõe esse conceito (pendência P-05).'),
    );
    return box;
  }

  // --- Tela 5: sincronizacao -----------------------------------------------
  private syncScreen(): HTMLElement {
    const box = h('section', { 'aria-label': 'Sincronização' });
    const view = syncView(this.pendingCount(), this.ctl.lastSyncRun());
    box.append(h('p', {}, `Leituras pendentes de envio: ${view.pendingCount}`));
    box.append(
      h('p', {},
        view.lastRunLabel
          ? `Última sincronização desta sessão: ${view.lastRunLabel}`
          : 'Nenhuma sincronização executada nesta sessão.'),
    );
    box.append(
      h('p', {},
        'A diretiva atual do aparelho (NONE/RETIRE/BLOCK) só é conhecida no contato com o servidor, ' +
        'durante a sincronização — o SyncEngine da ED-18 não expõe consulta separada (pendência P-06).'),
    );
    // Legenda das diretivas possiveis (directiveLabel ligada de verdade:
    // explica cada valor do contrato SEM afirmar qual esta ativa — a
    // diretiva vigente nao e recuperavel do resultado de syncRoute).
    const legend = h('ul', { class: 'campo-list', 'aria-label': 'Diretivas possíveis do servidor' });
    for (const d of ['NONE', 'RETIRE', 'BLOCK'] as const) {
      legend.append(h('li', {}, `${d} — ${directiveLabel(d)}`));
    }
    box.append(legend);
    box.append(
      button('Sincronizar agora', () => {
        if (this.ctl.isSyncing()) return;
        void this.ctl.syncNow().then((run) => {
          this.say(
            run.wiped
              ? `Sincronização concluída: ${run.uploaded} enviada(s); dados locais apagados por diretiva do servidor.`
              : `Sincronização concluída: ${run.uploaded} leitura(s) enviada(s).`,
          );
          this.render();
        }).catch((err: unknown) => {
          this.say(`Falha na sincronização: ${err instanceof Error ? err.message : String(err)}`);
          this.render();
        });
      }, this.ctl.isSyncing() ? { disabled: true } : {}),
    );
    return box;
  }

  private pendingCount(): number {
    return this.ctl.progress().rows.filter((r) => r.state === 'LIDA').length;
  }
}
