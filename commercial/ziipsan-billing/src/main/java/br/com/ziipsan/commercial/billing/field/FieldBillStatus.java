package br.com.ziipsan.commercial.billing.field;

/**
 * Situacao da conta de campo no servidor (ED-19). A conta IMPRESSA nunca
 * muda de valor: correcao e sempre uma NOVA versao (fluxo da P2.20), e o
 * registro guarda os dois fingerprints — o impresso e o da versao vigente.
 */
public enum FieldBillStatus {

    /** Recalculo do servidor identico ao impresso: conta confirmada. */
    CONFIRMED,
    /** Divergencia (de base ou de valor): conta na fila, aguardando aprovacao. */
    DIVERGENT_QUEUED,
    /** Critica de OCR (ED-18) segurou a leitura: emissao nao confirmada. */
    OCR_HELD,
    /** Tipo exige assinatura (P3.34) e o aparelho nao tem o certificado. */
    CERTIFICATE_MISSING,
    /** Divergencia aprovada: nova versao emitida, impressa preservada. */
    CORRECTED_BY_NEW_VERSION
}
