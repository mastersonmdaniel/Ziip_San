package br.com.ziipsan.commercial.registry.recadastramento;

/**
 * Codigos fechados de erro de coleta do recadastramento (ED-16): cada
 * submissao de campo recusada na sincronizacao vira uma linha de erro
 * com um destes codigos — e a materia-prima do relatorio de erros no
 * recadastramento (TR-685/SG-911). Codigo novo entra por nova versao
 * deste catalogo, como nos catalogos da P2.8/P3.32.
 */
public enum CollectionErrorCode {

    /** Campo fora do mapa de campos pre-definidos da P1.3/P1.31. */
    FIELD_NOT_PREDEFINED,
    /** Valor coletado vazio ou so com espacos. */
    VALUE_BLANK,
    /** A unidade da coleta nao e alvo do lote. */
    UNIT_NOT_IN_BATCH,
    /** Mesmo task_id reenviado com carga diferente da primeira coleta. */
    TASK_PAYLOAD_CONFLICT,
    /** Campo de documento coletado sem a mascara exigida pela P1.3. */
    DOCUMENT_NOT_MASKED,
    /** Lote nao esta aberto para coleta (ja encerrado). */
    BATCH_NOT_OPEN,
    /** Alvo incompativel com o campo (campo de cliente sem cliente, etc.). */
    TARGET_MISMATCH
}
