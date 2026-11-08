/**
 * Pipeline de geracao do app do cliente por tenant (ED-39; ADR-0026):
 * a partir da configuracao do tenant (slug, marca da P1.19/P3.21, host
 * do link, base do tenant), gera TODOS os artefatos carimbados que o
 * build das lojas consome. Funcoes puras: o passo de escrita em disco
 * e do runner da implantacao; aqui vive a derivacao, com os MESMOS
 * valores que o servidor espera (ClientAppConfig do Java):
 *  - identificador: br.com.ziipsan.cliente.<slug>
 *  - client Keycloak: ziipsan-cliente-<slug> (realm ziipsan-portal)
 *  - redirect: https://<host>/auth/callback
 *
 * Correcao D2 da revisao cruzada: a versao original gerava apenas
 * capacitor.config.ts, os dois arquivos de associacao, colors.xml e o
 * JSON do client — o AndroidManifest.xml (carimbo __TENANT_LINK_HOST__
 * e do nome), o index.html, o fragmento de Info.plist e o tema
 * completo (TENANT_THEME) NAO eram carimbados, o icone era apenas
 * referenciado (icon_asset_id da V11_1) sem artefato gerado, e o
 * portao de publicacao so varria os artefatos gerados. Agora o
 * pipeline gera e carimba TODOS os pontos:
 *  - capacitor.config.ts (appId + nome);
 *  - androidManifestXml (nome no label + host do App Link);
 *  - indexHtml (nome no <title>);
 *  - infoPlistFragmentXml (nome, identificador e host do Universal
 *    Link como Associated Domain);
 *  - tenantThemeTs (TENANT_THEME: nome + as TRES cores da marca —
 *    primaria, sobre-primaria e texto — mesmas variaveis da P3.21);
 *  - androidColorsXml (as tres cores no Android);
 *  - iconSvg (icone GERADO: vetor mestre deterministico com a cor
 *    primaria da marca e o monograma do nome — a rasterizacao nos
 *    tamanhos das lojas e passo do build/Capacitor Assets na
 *    integracao, declarado no relatorio);
 *  - assetlinks.json + apple-app-site-association;
 *  - JSON do client do Keycloak.
 * E assertPublishable varre o RESULTADO COMPLETO (todos os artefatos).
 *
 * MARCADORES DECLARADOS: a digital do certificado Android e o Team ID
 * da Apple NAO sao conhecidos nesta etapa (sao da conta do prestador,
 * ato externo): entram como marcadores __ANDROID_CERT_SHA256__ e
 * __APPLE_TEAM_ID__ nos arquivos de associacao, e a publicacao e
 * recusada enquanto qualquer marcador restar em QUALQUER artefato
 * (ver assertPublishable).
 */

export const APP_ID_PREFIX = 'br.com.ziipsan.cliente.';
export const KEYCLOAK_REALM = 'ziipsan-portal';

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const HEX = /^#[0-9A-Fa-f]{6}$/;

export interface TenantAppInput {
  tenantSlug: string;
  displayName: string;
  primaryColor: string;
  /** Cor do conteudo sobre a primaria (marca da P1.19/P3.21). */
  onPrimaryColor: string;
  /** Cor de texto da marca (marca da P1.19/P3.21). */
  brandTextColor: string;
  universalLinkHost: string;
  tenantBaseUrl: string;
}

export interface GeneratedApp {
  appId: string;
  keycloakClientId: string;
  redirectUri: string;
  capacitorConfigTs: string;
  androidManifestXml: string;
  indexHtml: string;
  infoPlistFragmentXml: string;
  tenantThemeTs: string;
  androidAssetLinksJson: string;
  appleAppSiteAssociation: string;
  androidColorsXml: string;
  iconSvg: string;
  keycloakClientJson: string;
}

function assertHex(field: string, value: string): void {
  if (!HEX.test(value ?? '')) {
    throw new Error(`cor ${field} fora do formato #RRGGBB: ${value}`);
  }
}

function assertInput(input: TenantAppInput): void {
  if (!SLUG.test(input.tenantSlug ?? '')) {
    throw new Error(`slug do tenant invalido: ${input.tenantSlug}`);
  }
  if (!input.displayName || input.displayName.trim() === '') {
    throw new Error('nome de exibicao do app e obrigatorio');
  }
  assertHex('primaryColor', input.primaryColor);
  assertHex('onPrimaryColor', input.onPrimaryColor);
  assertHex('brandTextColor', input.brandTextColor);
  if (!input.universalLinkHost || input.universalLinkHost.includes('://') || input.universalLinkHost.includes('/')) {
    throw new Error(`host do Universal Link/App Link invalido: ${input.universalLinkHost}`);
  }
  if (!input.tenantBaseUrl?.startsWith('https://')) {
    throw new Error(`base do tenant deve ser https: ${input.tenantBaseUrl}`);
  }
}

function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function generateApp(input: TenantAppInput): GeneratedApp {
  assertInput(input);
  const appId = APP_ID_PREFIX + input.tenantSlug;
  const clientId = `ziipsan-cliente-${input.tenantSlug}`;
  const redirectUri = `https://${input.universalLinkHost}/auth/callback`;
  const name = input.displayName;
  const primary = input.primaryColor.toUpperCase();
  const onPrimary = input.onPrimaryColor.toUpperCase();
  const brandText = input.brandTextColor.toUpperCase();

  const capacitorConfigTs = [
    `import type { CapacitorConfig } from '@capacitor/cli';`,
    ``,
    `// GERADO pelo pipeline da ED-39 para o tenant ${input.tenantSlug} — nao editar a mao.`,
    `const config: CapacitorConfig = {`,
    `  appId: '${appId}',`,
    `  appName: '${name.replace(/'/g, '')}',`,
    `  webDir: 'dist',`,
    `  android: { allowMixedContent: false },`,
    `};`,
    ``,
    `export default config;`,
    ``,
  ].join('\n');

  // Carimbo do AndroidManifest.xml do template: nome no label e host
  // do App Link no intent-filter autoVerify (correcao D2).
  const androidManifestXml = [
    `<?xml version="1.0" encoding="utf-8"?>`,
    `<!-- GERADO pelo pipeline da ED-39 para o tenant ${input.tenantSlug} — nao editar a mao. -->`,
    `<manifest xmlns:android="http://schemas.android.com/apk/res/android">`,
    ``,
    `    <uses-permission android:name="android.permission.INTERNET" />`,
    `    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`,
    ``,
    `    <application`,
    `        android:allowBackup="false"`,
    `        android:icon="@mipmap/ic_launcher"`,
    `        android:label="${escapeXml(name)}"`,
    `        android:usesCleartextTraffic="false">`,
    ``,
    `        <activity`,
    `            android:name=".MainActivity"`,
    `            android:exported="true"`,
    `            android:launchMode="singleTask">`,
    `            <intent-filter>`,
    `                <action android:name="android.intent.action.MAIN" />`,
    `                <category android:name="android.intent.category.LAUNCHER" />`,
    `            </intent-filter>`,
    `            <intent-filter android:autoVerify="true">`,
    `                <action android:name="android.intent.action.VIEW" />`,
    `                <category android:name="android.intent.category.DEFAULT" />`,
    `                <category android:name="android.intent.category.BROWSABLE" />`,
    `                <data`,
    `                    android:scheme="https"`,
    `                    android:host="${escapeXml(input.universalLinkHost)}"`,
    `                    android:pathPrefix="/auth/callback" />`,
    `            </intent-filter>`,
    `        </activity>`,
    `    </application>`,
    `</manifest>`,
    ``,
  ].join('\n');

  // Carimbo do index.html do template: nome no <title> (correcao D2).
  const indexHtml = [
    `<!doctype html>`,
    `<html lang="pt-BR">`,
    `  <head>`,
    `    <meta charset="utf-8" />`,
    `    <meta name="viewport" content="viewport-fit=cover" />`,
    `    <meta name="theme-color" content="${primary}" />`,
    `    <title>${escapeXml(name)}</title>`,
    `  </head>`,
    `  <body>`,
    `    <!-- GERADO pelo pipeline da ED-39 para o tenant ${input.tenantSlug} — nao editar a mao. -->`,
    `    <main id="app"></main>`,
    `    <script type="module" src="/src/main.ts"></script>`,
    `  </body>`,
    `</html>`,
    ``,
  ].join('\n');

  // Carimbo do fragmento de Info.plist: nome, identificador e o host
  // como Associated Domain do Universal Link (correcao D2).
  const infoPlistFragmentXml = [
    `<?xml version="1.0" encoding="UTF-8"?>`,
    `<!-- GERADO pelo pipeline da ED-39 para o tenant ${input.tenantSlug} — nao editar a mao.`,
    `     Universal Link: Associated Domain applinks:${input.universalLinkHost} nas`,
    `     Capabilities/entitlements; association file publicado no host.`,
    `     Refresh token no Keychain via plugin SecureToken (RNF-39). -->`,
    `<plist version="1.0">`,
    `<dict>`,
    `  <key>CFBundleDisplayName</key>`,
    `  <string>${escapeXml(name)}</string>`,
    `  <key>CFBundleIdentifier</key>`,
    `  <string>${appId}</string>`,
    `  <key>com.apple.developer.associated-domains</key>`,
    `  <array>`,
    `    <string>applinks:${escapeXml(input.universalLinkHost)}</string>`,
    `  </array>`,
    `</dict>`,
    `</plist>`,
    ``,
  ].join('\n');

  // Tema completo carimbado (TENANT_THEME): nome + tres cores, no
  // mesmo formato de TenantTheme do app (src/branding/tenantTheme.ts).
  const tenantThemeTs = [
    `import type { TenantTheme } from './tenantTheme.ts';`,
    ``,
    `// GERADO pelo pipeline da ED-39 para o tenant ${input.tenantSlug} — nao editar a mao.`,
    `// Destino: src/branding/tenantTheme.generated.ts (escrito pelo runner).`,
    `// Tema da marca do tenant (P1.19/P3.21, core V11_1): nome e cores.`,
    `export const TENANT_THEME: TenantTheme = {`,
    `  displayName: '${name.replace(/'/g, '')}',`,
    `  primaryColor: '${primary}',`,
    `  onPrimaryColor: '${onPrimary}',`,
    `  brandTextColor: '${brandText}',`,
    `};`,
    ``,
  ].join('\n');

  const androidAssetLinksJson = JSON.stringify(
    [
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: appId,
          sha256_cert_fingerprints: ['__ANDROID_CERT_SHA256__'],
        },
      },
    ],
    null,
    2,
  );

  const appleAppSiteAssociation = JSON.stringify(
    {
      applinks: {
        details: [
          {
            appID: `__APPLE_TEAM_ID__.${appId}`,
            paths: ['/auth/callback'],
          },
        ],
      },
    },
    null,
    2,
  );

  const androidColorsXml = [
    `<?xml version="1.0" encoding="utf-8"?>`,
    `<!-- GERADO pelo pipeline da ED-39 (marca do tenant ${input.tenantSlug}). -->`,
    `<resources>`,
    `    <color name="ziipsan_brand_primary">${primary}</color>`,
    `    <color name="ziipsan_brand_on_primary">${onPrimary}</color>`,
    `    <color name="ziipsan_brand_text">${brandText}</color>`,
    `</resources>`,
    ``,
  ].join('\n');

  // Icone GERADO (correcao D2): vetor mestre deterministico — fundo na
  // cor primaria da marca, monograma (inicial do nome) na cor
  // sobre-primaria. Rasterizacao (PNG/mdpi..xxxhdpi, AppIcon iOS) e do
  // build com Capacitor Assets na integracao.
  const monogram = escapeXml(name.trim().charAt(0).toUpperCase());
  const iconSvg = [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-label="${escapeXml(name)}">`,
    `  <!-- GERADO pelo pipeline da ED-39 (icone do tenant ${input.tenantSlug}). -->`,
    `  <rect width="512" height="512" rx="96" fill="${primary}"/>`,
    `  <text x="256" y="340" font-family="Arial, Helvetica, sans-serif" font-size="256" font-weight="700" fill="${onPrimary}" text-anchor="middle">${monogram}</text>`,
    `</svg>`,
    ``,
  ].join('\n');

  const keycloakClientJson = JSON.stringify(
    {
      realm: KEYCLOAK_REALM,
      clientId,
      publicClient: true,
      standardFlowEnabled: true,
      implicitFlowEnabled: false,
      directAccessGrantsEnabled: false,
      attributes: { 'pkce.code.challenge.method': 'S256' },
      redirectUris: [redirectUri],
      webOrigins: [],
    },
    null,
    2,
  );

  return {
    appId,
    keycloakClientId: clientId,
    redirectUri,
    capacitorConfigTs,
    androidManifestXml,
    indexHtml,
    infoPlistFragmentXml,
    tenantThemeTs,
    androidAssetLinksJson,
    appleAppSiteAssociation,
    androidColorsXml,
    iconSvg,
    keycloakClientJson,
  };
}

/** Todos os artefatos de conteudo do resultado (o portao varre o todo). */
export function generatedArtifacts(generated: GeneratedApp): Record<string, string> {
  return {
    capacitorConfigTs: generated.capacitorConfigTs,
    androidManifestXml: generated.androidManifestXml,
    indexHtml: generated.indexHtml,
    infoPlistFragmentXml: generated.infoPlistFragmentXml,
    tenantThemeTs: generated.tenantThemeTs,
    androidAssetLinksJson: generated.androidAssetLinksJson,
    appleAppSiteAssociation: generated.appleAppSiteAssociation,
    androidColorsXml: generated.androidColorsXml,
    iconSvg: generated.iconSvg,
    keycloakClientJson: generated.keycloakClientJson,
  };
}

/** Marcadores externos ainda nao preenchidos em QUALQUER artefato gerado. */
export function pendingMarkers(generated: GeneratedApp): string[] {
  const all = Object.values(generatedArtifacts(generated)).join('\n');
  const found = all.match(/__[A-Z0-9_]+__/g) ?? [];
  return [...new Set(found)];
}

/**
 * Publicacao exige zero marcadores pendentes no resultado COMPLETO:
 * digital do certificado e Team ID reais vem da conta do prestador
 * (ato externo declarado).
 */
export function assertPublishable(generated: GeneratedApp): void {
  const pending = pendingMarkers(generated);
  if (pending.length > 0) {
    throw new Error(`artefatos com marcadores externos pendentes: ${pending.join(', ')}`);
  }
}
