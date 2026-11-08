/**
 * Atualizacao remota do app do cliente (ED-39): a MESMA regra PLT-33
 * da ED-18, nas duas lojas — so codigo interpretado (bundle web), so
 * sem mudar o proposito do app, sem Appflow. Nas lojas ha ainda a
 * regra da plataforma: conteudo baixado nao pode mudar a funcionalidade
 * aprovada (politica do Google Play e diretriz 2.5.2/3.2.5 da App
 * Store) — mudanca nativa, de permissoes ou de proposito exige build
 * novo submetido a loja.
 */
export interface RemoteBundleManifest {
  appId: string;
  version: string;
  purposeHash: string;
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
