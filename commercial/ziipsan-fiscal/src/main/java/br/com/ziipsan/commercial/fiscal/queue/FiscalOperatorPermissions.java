package br.com.ziipsan.commercial.fiscal.queue;

/**
 * Permissões PRÓPRIAS de cada ação do operador sobre a fila (aceite da P2.33: "cada
 * uma com permissão própria"), no catálogo {@code recurso:ação} da P0.13. Ler a fila,
 * reenviar, inutilizar numeração e pedir cancelamento são concessões distintas: ter
 * uma NÃO concede outra, e o serviço confere a da ação antes de qualquer efeito.
 */
public final class FiscalOperatorPermissions {

    public static final String READ = "fiscal:document-read";
    public static final String RESEND = "fiscal:document-resend";
    public static final String VOID_NUMBER = "fiscal:number-void";
    public static final String REQUEST_CANCELLATION = "fiscal:cancellation-request";

    private FiscalOperatorPermissions() {
    }
}
