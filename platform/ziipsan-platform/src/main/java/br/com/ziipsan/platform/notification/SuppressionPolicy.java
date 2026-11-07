package br.com.ziipsan.platform.notification;

/**
 * Política de supressão do tenant ({@code msg.suppression_policy}, classe b):
 * quais eventos do provedor colocam o endereço na lista de supressão DO tenant
 * (a lista é por tenant — ver {@link DeliveryStore}). Modelada como os dois fatos
 * que o aceite nomeia, sem inventar catálogo de opções: rejeição e reclamação são
 * os únicos eventos que podem suprimir, e a política diz, para cada um, se suprime.
 * Endereço suprimido não recebe novo envio enquanto a supressão vigorar.
 */
public record SuppressionPolicy(boolean suppressOnBounce, boolean suppressOnComplaint) {

    public boolean suppresses(EmailEventType type) {
        return switch (type) {
            case BOUNCED -> suppressOnBounce;
            case COMPLAINED -> suppressOnComplaint;
            case DELIVERED -> false;
        };
    }
}
