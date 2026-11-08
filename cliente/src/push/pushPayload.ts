/**
 * Push SEM dado pessoal no app do cliente (ED-39; alertas da ED-17,
 * GES-27): espelho TypeScript de PushPayloadSanitizer (Java), com os
 * mesmos vetores. O payload so carrega codigos; titulo/corpo sao
 * chaves resolvidas no aparelho para textos genericos versionados
 * com o app — o nome do consumidor, o documento, o endereco e o valor
 * da conta NUNCA atravessam o servico de push (APNs/FCM).
 *
 * Correcao D1 da revisao cruzada: titleKey/bodyKey NAO aceitam texto
 * livre — devem ser exatamente a chave de modelo FECHADA do proprio
 * alerta (push.<modelo>.title/.body, de TEMPLATE_STEM_BY_ALERT_CODE).
 * Nome ("Ola Maria Silva, sua conta venceu") e endereco ("Rua das
 * Flores 123") em texto livre sao recusados; os regexes de CPF/
 * e-mail/telefone/valor seguem apenas como defesa adicional.
 */
export const ALLOWED_ALERT_CODES = new Set([
  'telemetry.meter-alarm',
  'telemetry.continuous-consumption',
  'telemetry.peak-consumption',
  'bill.available',
  'outage.scheduled',
  'outage.restored',
]);

/** Mapa FECHADO alerta -> modelo: unico par titleKey/bodyKey aceito por alerta. */
export const TEMPLATE_STEM_BY_ALERT_CODE: Record<string, string> = {
  'telemetry.meter-alarm': 'meter-alarm',
  'telemetry.continuous-consumption': 'continuous',
  'telemetry.peak-consumption': 'peak',
  'bill.available': 'bill',
  'outage.scheduled': 'outage-scheduled',
  'outage.restored': 'outage-restored',
};

export const ALLOWED_TEMPLATE_KEYS = new Set(
  Object.values(TEMPLATE_STEM_BY_ALERT_CODE).flatMap((stem) => [
    `push.${stem}.title`,
    `push.${stem}.body`,
  ]),
);

export const ALLOWED_DEEP_LINKS = new Set(['/consumo', '/conta', '/avisos', '/inicio']);

const ALLOWED_KEYS = new Set(['alertCode', 'titleKey', 'bodyKey', 'deepLink']);
const CPF = /\d{3}[.]\d{3}[.]\d{3}-\d{2}|\b\d{11}\b/;
const EMAIL = /[^\s@]+@[^\s@]+[.][^\s@]+/;
const MONEY = /R\$\s?[\d.,]+/;
const PHONE = /\+?\d[\d\s()\-]{7,}\d/;

export interface PushPayload {
  alertCode: string;
  titleKey?: string;
  bodyKey?: string;
  deepLink?: string;
}

export function sanitizePushPayload(raw: Record<string, string>): PushPayload {
  for (const key of Object.keys(raw)) {
    if (!ALLOWED_KEYS.has(key)) {
      throw new Error(`chave fora do contrato de push sem dado pessoal: ${key}`);
    }
  }
  if (!ALLOWED_ALERT_CODES.has(raw.alertCode ?? '')) {
    throw new Error(`codigo de alerta fora da lista do push: ${raw.alertCode}`);
  }
  if (raw.deepLink !== undefined && !ALLOWED_DEEP_LINKS.has(raw.deepLink)) {
    throw new Error(`deep link fora das telas permitidas do push: ${raw.deepLink}`);
  }
  // Camada principal (D1): titleKey/bodyKey so podem ser a chave de
  // modelo fechada do proprio alerta — nunca texto livre.
  const stem = TEMPLATE_STEM_BY_ALERT_CODE[raw.alertCode ?? ''];
  if (raw.titleKey !== undefined && raw.titleKey !== `push.${stem}.title`) {
    throw new Error(`titleKey fora do modelo fechado do alerta ${raw.alertCode}: ${raw.titleKey}`);
  }
  if (raw.bodyKey !== undefined && raw.bodyKey !== `push.${stem}.body`) {
    throw new Error(`bodyKey fora do modelo fechado do alerta ${raw.alertCode}: ${raw.bodyKey}`);
  }
  // Defesa adicional: regexes de dado pessoal em qualquer valor.
  for (const [key, value] of Object.entries(raw)) {
    if (CPF.test(value) || EMAIL.test(value) || MONEY.test(value) || PHONE.test(value)) {
      throw new Error(`valor com cara de dado pessoal recusado no push (chave ${key})`);
    }
  }
  return {
    alertCode: raw.alertCode,
    titleKey: raw.titleKey,
    bodyKey: raw.bodyKey,
    deepLink: raw.deepLink,
  };
}

/** Textos genericos locais por codigo de alerta (sem nenhum dado do consumidor). */
export const LOCAL_PUSH_TEXTS: Record<string, { title: string; body: string }> = {
  'telemetry.continuous-consumption': {
    title: 'Consumo continuo detectado',
    body: 'Notamos consumo sem pausa nas ultimas 24 horas. Toque para conferir.',
  },
  'telemetry.peak-consumption': {
    title: 'Pico de consumo',
    body: 'Notamos um pico de consumo na sua ligacao. Toque para conferir.',
  },
  'telemetry.meter-alarm': {
    title: 'Aviso sobre o medidor',
    body: 'Ha um aviso sobre o medidor da sua ligacao. Toque para conferir.',
  },
  'bill.available': {
    title: 'Nova conta disponivel',
    body: 'Sua nova conta ja esta disponivel no app.',
  },
  'outage.scheduled': {
    title: 'Interrupcao programada',
    body: 'Ha uma interrupcao programada para a sua regiao. Toque para ver os detalhes.',
  },
  'outage.restored': {
    title: 'Abastecimento restabelecido',
    body: 'O abastecimento da sua regiao foi restabelecido.',
  },
};
