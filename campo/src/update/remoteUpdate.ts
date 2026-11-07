/**
 * Atualizacao remota do app de campo (ED-18; PLT-33): SO codigo interpretado
 * (o bundle web), SO se nao alterar o proposito do app, e SEM depender do
 * Ionic Appflow — a distribuicao e do proprio ZiipSan (manifesto assinado no
 * servidor do tenant/plataforma). Qualquer mudanca nativa, de permissoes ou de
 * proposito exige APK novo assinado, pelo CI.
 */
export interface RemoteBundleManifest {
  appId: string;
  version: string;
  /** Hash da declaracao de proposito do app, gravada na casca nativa instalada. */
  purposeHash: string;
  /** Tipos de conteudo do pacote: so 'web-bundle' e aceito remotamente. */
  contents: string[];
  signature: string;
}

export interface InstalledShell {
  appId: string;
  purposeHash: string;
}

export type RemoteUpdateDecision =
  | { allowed: true }
  | { allowed: false; reason: 'app-id-mismatch' | 'purpose-change' | 'native-content' | 'unsigned' };

export function assertRemoteUpdateAllowed(
  manifest: RemoteBundleManifest,
  installed: InstalledShell,
): RemoteUpdateDecision {
  if (!manifest.signature) return { allowed: false, reason: 'unsigned' };
  if (manifest.appId !== installed.appId) return { allowed: false, reason: 'app-id-mismatch' };
  if (manifest.purposeHash !== installed.purposeHash) return { allowed: false, reason: 'purpose-change' };
  if (manifest.contents.some((c) => c !== 'web-bundle')) return { allowed: false, reason: 'native-content' };
  return { allowed: true };
}
