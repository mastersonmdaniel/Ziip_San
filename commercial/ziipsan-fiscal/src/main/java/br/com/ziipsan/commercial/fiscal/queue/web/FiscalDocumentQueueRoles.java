package br.com.ziipsan.commercial.fiscal.queue.web;

/**
 * Amarras de permissão da tela de Documentos fiscais (P2.33), sobre o catálogo
 * {@code recurso:ação} da P0.13 — os MESMOS códigos que o serviço confere
 * ({@code FiscalOperatorPermissions}): ler não autoriza agir, e cada ação tem a sua
 * permissão própria, na leitura e na escrita (defesa em profundidade).
 */
final class FiscalDocumentQueueRoles {

    static final String READ = "hasAuthority('fiscal:document-read')";
    static final String RESEND = "hasAuthority('fiscal:document-resend')";
    static final String VOID_NUMBER = "hasAuthority('fiscal:number-void')";
    static final String REQUEST_CANCELLATION = "hasAuthority('fiscal:cancellation-request')";

    private FiscalDocumentQueueRoles() {
    }
}
