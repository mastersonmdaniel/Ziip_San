package br.com.ziipsan.platform.notification;

/**
 * Categoria de um aviso (P3.36). As categorias obrigatórias NÃO são fixas neste enum:
 * o conjunto vigente é o parâmetro {@code notify.mandatory_categories} (classe c, piso da
 * plataforma) resolvido por {@link NotificationPolicy#effectiveMandatoryCategories(java.util.Set)}.
 * O piso da plataforma é {@code SECURITY}, {@code PENDING_APPROVAL} e {@code LEGAL_NOTICE}
 * (aceite da P3.36); o tenant pode acrescentar categorias, nunca remover do piso.
 */
public enum NotificationCategory {
    SECURITY, PENDING_APPROVAL, LEGAL_NOTICE, BILLING, SERVICE_ORDER, ACCOUNT, GENERAL
}
