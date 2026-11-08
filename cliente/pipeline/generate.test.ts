import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertPublishable, generateApp, pendingMarkers, type TenantAppInput } from './generate.ts';

const INPUT: TenantAppInput = {
  tenantSlug: 'samae-exemplo',
  displayName: 'Saneamento Exemplo',
  primaryColor: '#00548F',
  onPrimaryColor: '#FFFFFF',
  brandTextColor: '#10222E',
  universalLinkHost: 'app.exemplo.invalid',
  tenantBaseUrl: 'https://api.exemplo.invalid',
};

test('geracao deriva os mesmos identificador, client e redirect do servidor (Java)', () => {
  const g = generateApp(INPUT);
  assert.equal(g.appId, 'br.com.ziipsan.cliente.samae-exemplo');
  assert.equal(g.keycloakClientId, 'ziipsan-cliente-samae-exemplo');
  assert.equal(g.redirectUri, 'https://app.exemplo.invalid/auth/callback');
  assert.ok(g.capacitorConfigTs.includes(`appId: 'br.com.ziipsan.cliente.samae-exemplo'`));
  assert.ok(g.capacitorConfigTs.includes(`appName: 'Saneamento Exemplo'`));
  assert.ok(g.androidColorsXml.includes('#00548F'));
});

test('client gerado e publico, so authorization code, PKCE S256, redirect unico', () => {
  const kc = JSON.parse(generateApp(INPUT).keycloakClientJson);
  assert.equal(kc.realm, 'ziipsan-portal');
  assert.equal(kc.publicClient, true);
  assert.equal(kc.implicitFlowEnabled, false);
  assert.equal(kc.directAccessGrantsEnabled, false);
  assert.equal(kc.attributes['pkce.code.challenge.method'], 'S256');
  assert.deepEqual(kc.redirectUris, ['https://app.exemplo.invalid/auth/callback']);
});

test('arquivos de associacao ligam o link verificado ao app nas duas plataformas', () => {
  const g = generateApp(INPUT);
  const links = JSON.parse(g.androidAssetLinksJson);
  assert.equal(links[0].target.package_name, 'br.com.ziipsan.cliente.samae-exemplo');
  const aasa = JSON.parse(g.appleAppSiteAssociation);
  assert.ok(aasa.applinks.details[0].appID.endsWith('.br.com.ziipsan.cliente.samae-exemplo'));
  assert.deepEqual(aasa.applinks.details[0].paths, ['/auth/callback']);
});

test('marcadores externos pendentes bloqueiam a publicacao ate serem preenchidos', () => {
  const g = generateApp(INPUT);
  assert.deepEqual(pendingMarkers(g).sort(), ['__ANDROID_CERT_SHA256__', '__APPLE_TEAM_ID__']);
  assert.throws(() => assertPublishable(g), /marcadores externos pendentes/);
});

test('entrada invalida e recusada: slug, cor, host e base sem https', () => {
  assert.throws(() => generateApp({ ...INPUT, tenantSlug: 'Slug Invalido' }));
  assert.throws(() => generateApp({ ...INPUT, primaryColor: 'azul' }));
  assert.throws(() => generateApp({ ...INPUT, universalLinkHost: 'https://app.exemplo.invalid' }));
  assert.throws(() => generateApp({ ...INPUT, tenantBaseUrl: 'http://api.exemplo.invalid' }));
});

test('D2: AndroidManifest, index.html e Info.plist sao carimbados (nome, host, identificador)', () => {
  const g = generateApp(INPUT);
  assert.ok(g.androidManifestXml.includes('android:label="Saneamento Exemplo"'));
  assert.ok(g.androidManifestXml.includes('android:host="app.exemplo.invalid"'));
  assert.ok(g.androidManifestXml.includes('android:autoVerify="true"'));
  assert.ok(g.indexHtml.includes('<title>Saneamento Exemplo</title>'));
  assert.ok(g.infoPlistFragmentXml.includes('<string>Saneamento Exemplo</string>'));
  assert.ok(g.infoPlistFragmentXml.includes('<string>br.com.ziipsan.cliente.samae-exemplo</string>'));
  assert.ok(g.infoPlistFragmentXml.includes('applinks:app.exemplo.invalid'));
  for (const art of [g.androidManifestXml, g.indexHtml, g.infoPlistFragmentXml]) {
    assert.ok(!/__TENANT_/.test(art), 'artefato carimbado nao pode reter marcador de template');
  }
});

test('D2: tema completo carimbado — TENANT_THEME com as tres cores e colors.xml com as tres', () => {
  const g = generateApp(INPUT);
  assert.ok(g.tenantThemeTs.includes('TENANT_THEME'));
  assert.ok(g.tenantThemeTs.includes(`displayName: 'Saneamento Exemplo'`));
  assert.ok(g.tenantThemeTs.includes(`primaryColor: '#00548F'`));
  assert.ok(g.tenantThemeTs.includes(`onPrimaryColor: '#FFFFFF'`));
  assert.ok(g.tenantThemeTs.includes(`brandTextColor: '#10222E'`));
  assert.ok(g.androidColorsXml.includes('ziipsan_brand_on_primary'));
  assert.ok(g.androidColorsXml.includes('#FFFFFF'));
  assert.ok(g.androidColorsXml.includes('#10222E'));
  assert.throws(() => generateApp({ ...INPUT, onPrimaryColor: 'branco' }));
  assert.throws(() => generateApp({ ...INPUT, brandTextColor: '' }));
});

test('D2: icone e GERADO — vetor com a cor primaria, a sobre-primaria e o monograma do nome', () => {
  const g = generateApp(INPUT);
  assert.ok(g.iconSvg.startsWith('<svg'));
  assert.ok(g.iconSvg.includes('fill="#00548F"'));
  assert.ok(g.iconSvg.includes('fill="#FFFFFF"'));
  assert.ok(g.iconSvg.includes('>S</text>'));
});

test('D2: portao de publicacao varre o resultado completo (marcador injetado em artefato novo bloqueia)', () => {
  const g = generateApp(INPUT);
  const adulterado = { ...g, androidManifestXml: `${g.androidManifestXml}\n<!-- __MARCADOR_INJETADO__ -->` };
  assert.deepEqual(pendingMarkers(adulterado).sort(), [
    '__ANDROID_CERT_SHA256__',
    '__APPLE_TEAM_ID__',
    '__MARCADOR_INJETADO__',
  ]);
  assert.throws(() => assertPublishable(adulterado), /marcadores externos pendentes/);
});
