package br.com.ziipsan.platform.work;

/**
 * Tipos de item de trabalho da caixa unificada (P2.28, JOR-05/PLT-13): TODOS os tipos
 * nomeados no aceite, um por origem de trabalho com responsável e prazo. O tipo é código
 * estável gravado em {@code work_items.item_type} (CHECK da V1_11 lista exatamente estes
 * nomes — guarda no {@code WorkMigrationConsistencyTest}); tipo novo entra aqui, no CHECK
 * e no módulo de origem ao mesmo tempo, nunca como texto livre.
 */
public enum WorkItemType {
    /** Aprovação de OS do núcleo da P1.31. */
    SERVICE_ORDER_APPROVAL,
    /** Aprovação de retificação/manutenção de conta da P2.20. */
    BILL_RECTIFICATION_APPROVAL,
    /** Aprovação de parâmetro pela tela de parâmetros da P1.6 (JOR-25). */
    PARAMETER_APPROVAL,
    /** Aprovação de documento. */
    DOCUMENT_APPROVAL,
    /** Protocolo a vencer (P1.36). */
    PROTOCOL_DUE,
    /** OS atrasada (P1.31). */
    SERVICE_ORDER_OVERDUE,
    /** Conta retida — fila de análise da P2.20 (JOR-09). */
    HELD_BILL,
    /** Rejeição fiscal a tratar (fila da P2.16/P2.33). */
    FISCAL_REJECTION,
    /** Pacote GIS a revisar (P2.22). */
    GIS_PACKAGE_REVIEW,
    /** Ação corretiva (ocorrência/ação da P2.25). */
    CORRECTIVE_ACTION,
    /** Calibração vencendo. */
    CALIBRATION_DUE,
    /** Pedido de titular (direitos/alterações do titular). */
    HOLDER_REQUEST,
    /** Obrigação regulatória com prazo. */
    REGULATORY_OBLIGATION,
    /** Divergência de conciliação (P3.1). */
    RECONCILIATION_DIVERGENCE
}
