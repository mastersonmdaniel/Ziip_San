import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  packageProblems, targetFor, visibleFeatures, type GisFieldPackage,
} from './fieldPackage.ts';

// Pacote SINTETICO de ensaio (declarado): area e feicoes ficticias.
const PKG: GisFieldPackage = {
  packageId: 'pkg-sintetico-1',
  areaRef: 'AREA-ENSAIO-01',
  packageVersion: 3,
  issuedAt: '2026-11-08T08:00:00Z',
  validUntil: '2026-11-09T08:00:00Z',
  basemapRef: 'pmtiles://AREA-ENSAIO-01.pmtiles',
  features: [
    {
      featureId: 'feat-valvula-1', featureTable: 'gis_node', code: 'VAL-001',
      featureClass: 'VALVE', featureTypeCode: 'GATE_VALVE', baseVersion: 7,
      lifecycleStatus: 'IN_SERVICE', attributes: { dn_mm: 100, operational_status: 'OPEN' },
      geomWkt: 'POINT(-48.700001 -26.900001)', personal: false,
    },
    {
      featureId: 'feat-ramal-1', featureTable: 'gis_connection', code: 'RAM-001',
      featureClass: 'SERVICE_LINE', featureTypeCode: 'SERVICE_LINE', baseVersion: 2,
      lifecycleStatus: 'IN_SERVICE', attributes: { dn_mm: 25 },
      geomWkt: 'LINESTRING(-48.700001 -26.900001, -48.700100 -26.900100)', personal: true,
    },
  ],
  catalogTypeCodes: ['GATE_VALVE', 'SERVICE_LINE'],
  valueLists: { operational_status: ['OPEN', 'CLOSED'] },
  attributeSchemas: {},
  styleProfileKey: 'ZIIP',
  accuracyTargets: { VALVE: { hTargetM: 0.10, vTargetM: 0.05 } },
};
const NOW = '2026-11-08T12:00:00Z';

test('pacote vigente sem camada pessoal abre para quem so tem gis.view', () => {
  const pub = { ...PKG, features: PKG.features.filter((f) => !f.personal) };
  assert.deepEqual(packageProblems(pub, ['gis.view'], NOW), []);
});

test('camada pessoal no pacote de quem nao tem gis.view-personal e problema nomeado', () => {
  assert.deepEqual(packageProblems(PKG, ['gis.view', 'gis.edit'], NOW), ['PERSONAL_LAYER_FORBIDDEN']);
  assert.deepEqual(packageProblems(PKG, ['gis.view', 'gis.view-personal'], NOW), []);
});

test('feicoes visiveis escondem as pessoais sem a permissao (defesa em profundidade)', () => {
  assert.deepEqual(visibleFeatures(PKG, ['gis.view']).map((f) => f.featureId), ['feat-valvula-1']);
  assert.equal(visibleFeatures(PKG, ['gis.view', 'gis.view-personal']).length, 2);
});

test('pacote vencido nao abre para coleta nova', () => {
  assert.deepEqual(packageProblems(PKG, ['gis.view-personal'], '2026-11-10T00:00:00Z'), ['PACKAGE_EXPIRED']);
});

test('meta da classe vem do pacote; classe sem meta devolve null (as-built recusado)', () => {
  assert.deepEqual(targetFor(PKG, PKG.features[0]), { hTargetM: 0.10, vTargetM: 0.05 });
  assert.equal(targetFor(PKG, PKG.features[1]), null);
});
