/**
 * Porta P-04 (R2-M2) — gravacao da leitura executada na fila local.
 *
 * A porta e a delegacao para `LocalStore.saveReading` (syncEngine.ts), o
 * metodo que faltava no contrato da ED-18: a integracao monta a porta com
 * o store real e a injeta no controller (`saveReading`), e a leitura
 * confirmada deixa de viver so na memoria da sessao. A semantica e a do
 * proprio LocalStore: grava ou substitui pela chave taskId (upsert), de
 * modo que confirmar a mesma tarefa duas vezes nao duplica a fila — o
 * reenvio na sincronizacao usa o MESMO task_id e o servidor responde
 * duplicate=true (syncEngine.ts).
 *
 * O que segue pendencia (RG2-C-P4): a IMPLEMENTACAO SQLCipher do
 * LocalStore — este arquivo e a fiacao TS, nao o banco cifrado. Nenhum
 * armazenamento em memoria e entregue como producao: um store em memoria
 * perderia a leitura no reinicio, que e exatamente a falha do R2-M2.
 */
import type { PendingReading } from '../readings/routePackage.ts';
import type { LocalStore } from './syncEngine.ts';

/** Monta a porta P-04 sobre o LocalStore real da integracao. */
export function createSaveReading(store: LocalStore): (reading: PendingReading) => Promise<void> {
  return (reading: PendingReading) => store.saveReading(reading);
}
