/**
 * Tela de autoleitura do consumidor (P-II7-TELA) — DOM puro, mesmo
 * padrao de cliente/src/portal/screens.ts: TS sem framework, rotulo
 * associado, aria-live, alvos >= 48px via CSS. Mobile-first por
 * natureza (decisao de paridade registada no relatorio).
 * Estados honestos: PENDING_OCR mostra a pendencia do motor
 * (P-II7-OCR [SEM FONTE]) e NUNCA uma leitura fingida; score nulo
 * (sem leitura extraida) mostra "sem score" — nenhum score fabricado,
 * como SelfReadingOutcome.java declara.
 */
import type {
  SelfReadingOutcome,
  SelfReadingPort,
  SelfReadingScoreConfig,
  SelfReadingSubmission,
} from './transport.ts';
import {
  REASON_LABELS,
  STATUS_LABELS,
  formatComponent,
  formatDistance,
  formatScore,
  validateScoreConfig,
  validateSubmission,
} from './models.ts';

type El = HTMLElement;

function h(tag: string, text?: string): El {
  const el = document.createElement(tag) as El;
  if (text !== undefined) el.textContent = text;
  return el;
}

function field(labelText: string, id: string, type = 'text'): { wrap: El; input: HTMLInputElement } {
  const wrap = h('div');
  wrap.setAttribute('class', 'selfreading-field');
  const label = h('label', labelText);
  label.setAttribute('for', id);
  const input = document.createElement('input') as unknown as HTMLInputElement;
  input.setAttribute('id', id);
  input.setAttribute('type', type);
  wrap.appendChild(label);
  wrap.appendChild(input as unknown as El);
  return { wrap, input };
}

function statusRegion(): El {
  const s = h('p');
  s.setAttribute('role', 'status');
  s.setAttribute('aria-live', 'polite');
  s.setAttribute('class', 'selfreading-status');
  return s;
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

function button(text: string, onClick: () => void): El {
  const b = h('button', text);
  b.setAttribute('type', 'button');
  b.setAttribute('class', 'selfreading-action');
  b.addEventListener('click', onClick);
  return b;
}

function numOrNull(v: string): number | null {
  if (v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function intList(v: string): number[] {
  if (v.trim() === '') return [];
  return v.split(',').map((x) => Number(x.trim()));
}

/** Renderiza o desfecho tipificado completo (estado, motivos, score, geo, handoff). */
export function renderOutcome(out: El, v: SelfReadingOutcome): void {
  out.textContent = '';
  out.appendChild(h('p', `Estado: ${STATUS_LABELS[v.status]} (${v.status})`));
  if (v.status === 'PENDING_OCR') {
    out.appendChild(h('p',
      'A leitura da foto ainda não foi feita: aguarda o motor de leitura (OCR). Nenhuma leitura é apresentada sem o motor.'));
  }
  for (const r of v.reasons) {
    out.appendChild(h('p', `Motivo: ${REASON_LABELS[r]} (${r})`));
  }
  if (v.ocrReading === null) {
    out.appendChild(h('p', 'Leitura da foto: nenhuma leitura extraída.'));
  } else {
    out.appendChild(h('p', `Leitura da foto: ${v.ocrReading}`));
  }
  if (v.score === null) {
    out.appendChild(h('p', 'Score: sem score (não houve leitura extraída).'));
  } else {
    out.appendChild(h('p', `Score total: ${formatScore(v.score.total)}`));
    out.appendChild(h('p', `Componente OCR: ${formatComponent(v.score.ocrComponent)}`));
    out.appendChild(h('p', `Componente de coerência: ${formatComponent(v.score.coherenceComponent)}`));
    out.appendChild(h('p', `Componente da foto: ${formatComponent(v.score.photoComponent)}`));
    out.appendChild(h('p', `Componente geográfica: ${formatComponent(v.score.geoComponent)}`));
  }
  if (!v.geo.present) {
    out.appendChild(h('p', 'Prova geográfica: não medida (sem prova ou sem coordenadas da ligação).'));
  } else {
    out.appendChild(h('p', `Distância à ligação: ${formatDistance(v.geo.distanceMetres)}`));
    out.appendChild(h('p', `Dentro da distância máxima: ${v.geo.withinMaxDistance ? 'sim' : 'não'}`));
    out.appendChild(h('p', `Precisão suficiente: ${v.geo.accuracySufficient ? 'sim' : 'não'}`));
  }
  if (v.handoff !== null) {
    out.appendChild(h('p', `Leitura aceite para a crítica: ${v.handoff.currentReading}`));
    out.appendChild(h('p',
      v.handoff.measuredConsumption === null
        ? 'Consumo derivado: primeira leitura (sem consumo derivado).'
        : `Consumo derivado: ${v.handoff.measuredConsumption}`));
  }
}

export interface SelfReadingScreenDeps {
  port: SelfReadingPort;
  config: SelfReadingScoreConfig;
  newSubmissionId: () => string;
}

export function renderSelfReading(root: El, deps: SelfReadingScreenDeps): void {
  const s = h('section');
  const title = h('h2', 'Autoleitura');
  title.setAttribute('tabindex', '-1');
  s.appendChild(title);
  s.appendChild(h('p',
    'Envie a foto do mostrador e, se quiser, a leitura que você leu. A leitura da foto depende do motor de leitura (OCR); sem motor, a submissão fica pendente — nenhuma leitura é inventada.'));
  root.appendChild(s);

  const unit = field('Unidade consumidora', 'sr-unit');
  const reference = field('Competência (AAAA-MM)', 'sr-reference');
  const declared = field('Leitura declarada (opcional)', 'sr-declared', 'number');
  const previous = field('Leitura anterior (opcional; vazio = primeira leitura)', 'sr-previous', 'number');
  const history = field('Histórico de consumos (separados por vírgula)', 'sr-history');
  const photoRef = field('Referência da foto', 'sr-photo-ref');
  const photoHash = field('Hash SHA-256 da foto (64 hex)', 'sr-photo-hash');
  const photoW = field('Largura da foto (px)', 'sr-photo-w', 'number');
  const photoH = field('Altura da foto (px)', 'sr-photo-h', 'number');
  const geoLat = field('Latitude da captura (opcional)', 'sr-geo-lat', 'number');
  const geoLon = field('Longitude da captura (opcional)', 'sr-geo-lon', 'number');
  const geoAcc = field('Precisão do GPS em metros (com latitude/longitude)', 'sr-geo-acc', 'number');
  const geoAt = field('Instante da captura (ISO, com latitude/longitude)', 'sr-geo-at');
  const connLat = field('Latitude da ligação (opcional)', 'sr-conn-lat', 'number');
  const connLon = field('Longitude da ligação (opcional)', 'sr-conn-lon', 'number');
  const status = statusRegion();
  const out = h('div');
  for (const f of [unit, reference, declared, previous, history, photoRef, photoHash,
    photoW, photoH, geoLat, geoLon, geoAcc, geoAt, connLat, connLon]) {
    s.appendChild(f.wrap);
  }
  s.appendChild(status);
  s.appendChild(out);
  s.appendChild(button('Enviar autoleitura', () => {
    const hasPhoto = photoRef.input.value.trim() !== '';
    const hasGeo = geoLat.input.value.trim() !== '' && geoLon.input.value.trim() !== '';
    const hasConn = connLat.input.value.trim() !== '' && connLon.input.value.trim() !== '';
    const submission: SelfReadingSubmission = {
      id: deps.newSubmissionId(),
      unitId: unit.input.value.trim(),
      reference: reference.input.value.trim(),
      declaredReading: numOrNull(declared.input.value),
      photo: hasPhoto
        ? {
          artifactRef: photoRef.input.value.trim(),
          sha256Hex: photoHash.input.value.trim(),
          widthPx: Number(photoW.input.value),
          heightPx: Number(photoH.input.value),
        }
        : null,
      geoProof: hasGeo
        ? {
          position: { latitude: Number(geoLat.input.value), longitude: Number(geoLon.input.value) },
          accuracyMetres: Number(geoAcc.input.value),
          capturedAt: geoAt.input.value.trim(),
        }
        : null,
      connectionPosition: hasConn
        ? { latitude: Number(connLat.input.value), longitude: Number(connLon.input.value) }
        : null,
      previousReading: numOrNull(previous.input.value),
      history: intList(history.input.value),
    };
    const invalid = validateSubmission(submission) ?? validateScoreConfig(deps.config);
    if (invalid) { status.textContent = invalid; return; }
    out.textContent = '';
    status.textContent = 'Enviando…';
    void deps.port.evaluate(submission, deps.config).then((v) => {
      status.textContent = '';
      renderOutcome(out, v);
    }).catch((err: unknown) => {
      status.textContent = `Erro: ${errorMessage(err)}`;
    });
  }));
}
