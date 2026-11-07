import { FieldSession } from './auth/session.ts';
import { SyncEngine } from './sync/syncEngine.ts';

// Bootstrap minimo do app de campo (ED-18): a casca sobe, abre o banco local
// cifrado e expoe sessao/sincronizacao para a UI de rota. A UI completa de
// leitura (telas) cresce sobre esta base nas entregas seguintes (ED-19/ED-20)
// sem mudar os contratos daqui.
export interface CampoBootstrap {
  session: FieldSession;
  sync: SyncEngine;
}

export function describeBootstrap(): string {
  return 'ZiipSan Campo — base ED-18 (autenticacao PKCE, banco cifrado, sincronizacao por task_id)';
}
