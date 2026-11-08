import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  isPhotoUsable, isSha256Hex, photoProblems, registerFieldPhoto,
  type FaceBlurPort, type FieldPhoto,
} from './fieldPhoto.ts';
import type { GnssFix } from './gnss.ts';

const FIX: GnssFix = {
  transport: 'BLUETOOTH_NMEA', solution: 'RTK_FIXED',
  latitude: -26.900001, longitude: -48.700001, ellipsoidalHeightM: 812.40,
  hAccuracyM: 0.02, vAccuracyM: 0.03, pdop: 1.4, satellites: 18,
  antennaHeightM: 2.0, capturedAt: '2026-11-08T12:00:00Z',
};
const HASH = 'a'.repeat(64); // hash sintetico de ensaio (forma, nao conteudo)
const PHOTO: FieldPhoto = {
  localRef: 'file:///cifrado/fotos/f1.jpg', featureId: 'feat-valvula-1',
  sha256: HASH, fix: FIX, azimuthDeg: 135.5,
  capturedAt: '2026-11-08T12:00:00Z', scan: 'CLEARED', facesBlurred: true,
};

test('hash SHA-256 exige hex minusculo de 64 caracteres', () => {
  assert.equal(isSha256Hex(HASH), true);
  assert.equal(isSha256Hex('abc'), false);
  assert.equal(isSha256Hex('A'.repeat(64)), false);
});

test('foto varrida (CLEARED), com posicao, azimute, hash e desfoque e utilizavel', () => {
  assert.deepEqual(photoProblems(PHOTO, true), []);
  assert.equal(isPhotoUsable(PHOTO, true), true);
});

test('foto em quarentena ou infectada nao sustenta cadastro (varredura P1.20)', () => {
  assert.deepEqual(photoProblems({ ...PHOTO, scan: 'QUARANTINE' }, true), ['SCAN_NOT_CLEARED']);
  assert.deepEqual(photoProblems({ ...PHOTO, scan: 'INFECTED' }, true), ['SCAN_NOT_CLEARED']);
});

test('azimute fora de [0,360) e recusado', () => {
  assert.deepEqual(photoProblems({ ...PHOTO, azimuthDeg: 360 }, true), ['AZIMUTH_OUT_OF_RANGE']);
  assert.deepEqual(photoProblems({ ...PHOTO, azimuthDeg: -1 }, true), ['AZIMUTH_OUT_OF_RANGE']);
});

test('privacidade ED-20: desfoque ligado sem desfoque aplicado torna a foto inutilizavel', () => {
  assert.deepEqual(photoProblems({ ...PHOTO, facesBlurred: false }, true), ['BLUR_REQUIRED_NOT_APPLIED']);
});

test('privacidade ED-20: parametro de desfoque AUSENTE nao vira falso silencioso (contrato D-3)', () => {
  assert.deepEqual(photoProblems(PHOTO, null), ['BLUR_PARAM_MISSING']);
  assert.equal(isPhotoUsable(PHOTO, null), false);
  assert.equal(isPhotoUsable({ ...PHOTO, facesBlurred: false }, false), true);
});

// --- Correcao m1: facesBlurred DERIVA da FaceBlurPort (nunca autodeclarado) ---

const INPUT = {
  localRef: PHOTO.localRef, featureId: PHOTO.featureId, sha256: PHOTO.sha256,
  fix: PHOTO.fix, azimuthDeg: PHOTO.azimuthDeg, capturedAt: PHOTO.capturedAt, scan: PHOTO.scan,
} as const;

function spyPort(result: boolean): { port: FaceBlurPort; calls: () => number } {
  let calls = 0;
  return { port: { blurFaces: () => { calls += 1; return result; } }, calls: () => calls };
}

test('m1: registro invoca a FaceBlurPort e a flag nasce do retorno dela (true)', () => {
  const { port, calls } = spyPort(true);
  const { photo, problems } = registerFieldPhoto(INPUT, true, port);
  assert.deepEqual(problems, []);
  assert.equal(calls(), 1);
  assert.equal(photo?.facesBlurred, true);
  assert.equal(isPhotoUsable(photo!, true), true);
});

test('m1: porta devolvendo false, a foto nasce SEM desfoque e segue recusada', () => {
  const { port, calls } = spyPort(false);
  const { photo, problems } = registerFieldPhoto(INPUT, true, port);
  assert.deepEqual(problems, []);
  assert.equal(calls(), 1);
  assert.equal(photo?.facesBlurred, false);
  assert.deepEqual(photoProblems(photo!, true), ['BLUR_REQUIRED_NOT_APPLIED']);
});

test('m1: desfoque exigido SEM porta e recusa nomeada (nenhuma foto nasce)', () => {
  const { photo, problems } = registerFieldPhoto(INPUT, true, null);
  assert.equal(photo, null);
  assert.deepEqual(problems, ['BLUR_PORT_MISSING']);
});

test('m1: parametro ausente recusa pelo nome (D-3) e a porta nem e invocada', () => {
  const { port, calls } = spyPort(true);
  const { photo, problems } = registerFieldPhoto(INPUT, null, port);
  assert.equal(photo, null);
  assert.deepEqual(problems, ['BLUR_PARAM_MISSING']);
  assert.equal(calls(), 0);
});

test('m1: desfoque nao exigido nao invoca a porta e a foto nasce sem desfoque', () => {
  const { port, calls } = spyPort(true);
  const { photo, problems } = registerFieldPhoto(INPUT, false, port);
  assert.deepEqual(problems, []);
  assert.equal(calls(), 0);
  assert.equal(photo?.facesBlurred, false);
  assert.equal(isPhotoUsable(photo!, false), true);
});
