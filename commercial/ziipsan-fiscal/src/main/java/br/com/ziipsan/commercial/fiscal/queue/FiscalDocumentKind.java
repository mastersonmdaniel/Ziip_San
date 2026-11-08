package br.com.ziipsan.commercial.fiscal.queue;

/**
 * Tipos de documento que a fila de Documentos fiscais do tenant mostra pelo MESMO
 * modelo de documento fiscal da P2.6 (aceite da P2.33): a NFAg-e (modelo 75, estados
 * de {@code NfageStatus}) e a NFS-e avulsa da P3.6, que entra na mesma tela pelo mesmo
 * modelo — a avulsa nasce {@code GERADA_NAO_TRANSMITIDA} na P3.6 e, enquanto não
 * transmitida, é pendência de contingência/transmissão nesta fila, nunca um leiaute
 * próprio (fronteira expressa do aceite).
 */
public enum FiscalDocumentKind {
    NFAGE,
    NFSE_AVULSA
}
