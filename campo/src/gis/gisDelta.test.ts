import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildInsertDelta, buildUpdateDelta, deltaCaptureMethod, diffAttributes, isUuidV7 } from './gisDelta.ts';
import type { GisFieldPackage } from './fieldPackage.ts';
import type { GnssFix } from './gnss.ts';

const ID = '0192a1b4-0000-7000-8000-000000000001'; // UUIDv7 sintetico de ensaio
const PKG: GisFieldPackage = {
  packageId: 'pkg-sintetico-1', areaRef: 'AREA-ENSAIO-01', packageVersion: 3,
  issuedAt: '2026-11-08T08:00:00Z', validUntil: '2026-11-09T08:00:00Z',
  basemapRef: 'pmtiles://AREA-ENSAIO-01.pmtiles',
  features: [{
    featureId: 'feat-valvula-1', featureTable: 'gis_node', code: 'VAL-001',
    featureClass: 'VALVE', featureTypeCode: 'GATE_VALVE', baseVersion: 7,
    lifecycleStatus: 'IN_SERVICE', attributes: { dn_mm: 100, operational_status: 'OPEN' },
    geomWkt: 'POINT(-48.700001 -26.900001)', personal: false,
  }],
  catalogTypeCodes: ['GATE_VALVE'], valueLists: {}, attributeSchemas: {},
  styleProfileKey: 'ZIIP', accuracyTargets: { VALVE: { hTargetM: 0.10, vTargetM: 0.05 } },
};
const FEATURE = PKG.features[0];
const FIX: GnssFix = {
  transport: 'BLUETOOTH_NMEA', solution: 'RTK_FIXED',
  latitude: -26.900002, longitude: -48.700002, ellipsoidalHeightM: 812.41,
  hAccuracyM: 0.02, vAccuracyM: 0.03, pdop: 1.4, satellites: 18,
  antennaHeightM: 2.0, capturedAt: '2026-11-08T12:00:00Z',
};

test('client_change_id exige a forma UUIDv7 do desenho (versao 7 e variante)', () => {
  assert.equal(isUuidV7(ID), true);
  assert.equal(isUuidV7('0192a1b4-0000-4000-8000-000000000001'), false); // v4 nao serve
  assert.equal(isUuidV7('delta-1'), false);
});

test('diff por atributo: so os campos alterados entram, com o valor de base ao lado', () => {
  const { changed, baseOfChanged } = diffAttributes(
    { dn_mm: 100, operational_status: 'OPEN' },
    { dn_mm: 100, operational_status: 'CLOSED' },
  );
  assert.deepEqual(changed, { operational_status: 'CLOSED' });
  assert.deepEqual(baseOfChanged, { operational_status: 'OPEN' });
});

test('delta de edicao carrega versao de base, so campos alterados e o pacote FIELD', () => {
  const { delta, problems } = buildUpdateDelta({
    pkg: PKG, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'CLOSED' },
    geomWkt: null, fix: null, asBuilt: false,
    clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.deepEqual(problems, []);
  assert.equal(delta?.operation, 'UPDATE');
  assert.equal(delta?.baseVersion, 7);
  assert.deepEqual(delta?.changedAttributes, { operational_status: 'CLOSED' });
  assert.deepEqual(delta?.baseAttributes, { operational_status: 'OPEN' });
});

test('edicao sem nenhuma mudanca nao nasce (delta vazio recusado)', () => {
  const { delta, problems } = buildUpdateDelta({
    pkg: PKG, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'OPEN' },
    geomWkt: 'POINT(-48.700001 -26.900001)', fix: null, asBuilt: false,
    clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['EMPTY_DELTA']);
});

test('geometria nova sem fix GNSS nao nasce', () => {
  const { delta, problems } = buildUpdateDelta({
    pkg: PKG, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'OPEN' },
    geomWkt: 'POINT(-48.700002 -26.900002)', fix: null, asBuilt: false,
    clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['GEOMETRY_WITHOUT_FIX']);
});

test('as-built com RTK fixo dentro da meta nasce e declara o metodo de captura', () => {
  const { delta, problems } = buildUpdateDelta({
    pkg: PKG, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'OPEN' },
    geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX, asBuilt: true,
    clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.deepEqual(problems, []);
  assert.equal(delta?.asBuilt, true);
  assert.equal(deltaCaptureMethod(delta!), 'RTK_FIXED');
});

test('as-built recusado no aparelho sem RTK fixo (a mesma regra roda no servidor)', () => {
  const { delta, problems } = buildUpdateDelta({
    pkg: PKG, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'OPEN' },
    geomWkt: 'POINT(-48.700002 -26.900002)',
    fix: { ...FIX, solution: 'RTK_FLOAT' }, asBuilt: true,
    clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['AS_BUILT_FIX_REFUSED']);
});

test('as-built de classe sem meta no pacote e recusado pelo nome (meta nunca presumida)', () => {
  const semMeta: GisFieldPackage = { ...PKG, accuracyTargets: {} };
  const { delta, problems } = buildUpdateDelta({
    pkg: semMeta, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'OPEN' },
    geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX, asBuilt: true,
    clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['AS_BUILT_TARGET_MISSING']);
});

test('coleta nova (INSERT) nasce sem versao de base e com o fix da coleta', () => {
  const { delta, problems } = buildInsertDelta({
    pkg: PKG, featureId: 'feat-nova-1', featureTable: 'gis_node',
    attributes: { dn_mm: 50 }, geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX,
    asBuilt: false, clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.deepEqual(problems, []);
  assert.equal(delta?.operation, 'INSERT');
  assert.equal(delta?.baseVersion, null);
});

// --- Correcao M1: as-built de INSERT passa pelo MESMO portao de meta do UPDATE ---

test('M1: as-built de INSERT com RTK fixo DENTRO da meta da classe nasce', () => {
  const { delta, problems } = buildInsertDelta({
    pkg: PKG, featureId: 'feat-nova-1', featureTable: 'gis_node', featureClass: 'VALVE',
    attributes: { dn_mm: 50 }, geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX,
    asBuilt: true, clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.deepEqual(problems, []);
  assert.equal(delta?.asBuilt, true);
});

test('M1: as-built de INSERT FORA da meta e recusado (h 0,25 contra meta 0,10 da classe)', () => {
  const { delta, problems } = buildInsertDelta({
    pkg: PKG, featureId: 'feat-nova-1', featureTable: 'gis_node', featureClass: 'VALVE',
    attributes: { dn_mm: 50 }, geomWkt: 'POINT(-48.700002 -26.900002)',
    fix: { ...FIX, hAccuracyM: 0.25 },
    asBuilt: true, clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['AS_BUILT_FIX_REFUSED']);
});

test('M1: as-built de INSERT sem meta da classe no pacote e recusado pelo nome', () => {
  const semClasse = buildInsertDelta({
    pkg: PKG, featureId: 'feat-nova-1', featureTable: 'gis_node',
    attributes: { dn_mm: 50 }, geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX,
    asBuilt: true, clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(semClasse.delta, null);
  assert.deepEqual(semClasse.problems, ['AS_BUILT_TARGET_MISSING']);
  const classeSemMeta = buildInsertDelta({
    pkg: PKG, featureId: 'feat-nova-1', featureTable: 'gis_node', featureClass: 'SERVICE_LINE',
    attributes: { dn_mm: 50 }, geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX,
    asBuilt: true, clientChangeId: ID, capturedAt: '2026-11-08T12:00:00Z',
  });
  assert.equal(classeSemMeta.delta, null);
  assert.deepEqual(classeSemMeta.problems, ['AS_BUILT_TARGET_MISSING']);
});

// --- Correcao M2: validade do pacote imposta na construcao do delta ---

test('M2: pacote vencido recusa a construcao do UPDATE pelo nome (sem delta)', () => {
  const { delta, problems } = buildUpdateDelta({
    pkg: PKG, feature: FEATURE,
    editedAttributes: { dn_mm: 100, operational_status: 'CLOSED' },
    geomWkt: null, fix: null, asBuilt: false,
    clientChangeId: ID, capturedAt: '2026-11-10T12:00:00Z', // pacote vale ate 2026-11-09T08:00Z
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['PACKAGE_EXPIRED']);
});

test('M2: pacote vencido recusa a construcao do INSERT pelo nome (sem delta)', () => {
  const { delta, problems } = buildInsertDelta({
    pkg: PKG, featureId: 'feat-nova-1', featureTable: 'gis_node',
    attributes: { dn_mm: 50 }, geomWkt: 'POINT(-48.700002 -26.900002)', fix: FIX,
    asBuilt: false, clientChangeId: ID, capturedAt: '2026-11-10T12:00:00Z',
  });
  assert.equal(delta, null);
  assert.deepEqual(problems, ['PACKAGE_EXPIRED']);
});
