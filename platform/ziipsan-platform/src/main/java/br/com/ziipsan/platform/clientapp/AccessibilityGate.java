package br.com.ziipsan.platform.clientapp;

/**
 * Portao de acessibilidade do aparelho para liberacao do app do cliente
 * (ED-39; SG-782): o app usa os recursos de acessibilidade do aparelho
 * (TalkBack no Android, VoiceOver no iOS, fonte dinamica, reducao de
 * movimento) e a liberacao para a loja exige a conferencia registrada —
 * no mesmo espirito do portao {@code PortalCoverage} da ED-38 (que
 * EXIGE a varredura, sem executa-la): qualquer item falso bloqueia a
 * submissao; a varredura em aparelho real e ato do ensaio (POC-55).
 */
public final class AccessibilityGate {

    private AccessibilityGate() {
    }

    /** Conferencia SG-782 de um build candidato a submissao. */
    public record Checklist(
            boolean talkBackLabelsComplete,
            boolean voiceOverLabelsComplete,
            boolean dynamicTypeSupported,
            boolean minTouchTargetsOk,
            boolean contrastValidated,
            boolean reduceMotionRespected,
            boolean axeSeriousOrCriticalZero) {

        /** Todos os itens conferidos? So entao o build pode ser submetido. */
        public boolean releasable() {
            return talkBackLabelsComplete && voiceOverLabelsComplete && dynamicTypeSupported
                    && minTouchTargetsOk && contrastValidated && reduceMotionRespected
                    && axeSeriousOrCriticalZero;
        }
    }

    /** Exige a conferencia completa; lanca IllegalStateException nomeando o item faltante. */
    public static void assertReleasable(Checklist checklist) {
        if (checklist == null) {
            throw new IllegalStateException("conferencia de acessibilidade (SG-782) nao realizada");
        }
        if (!checklist.talkBackLabelsComplete()) {
            throw new IllegalStateException("SG-782: rotulos do TalkBack incompletos");
        }
        if (!checklist.voiceOverLabelsComplete()) {
            throw new IllegalStateException("SG-782: rotulos do VoiceOver incompletos");
        }
        if (!checklist.dynamicTypeSupported()) {
            throw new IllegalStateException("SG-782: fonte dinamica do aparelho nao respeitada");
        }
        if (!checklist.minTouchTargetsOk()) {
            throw new IllegalStateException("SG-782: alvos de toque abaixo do minimo (48dp/44pt)");
        }
        if (!checklist.contrastValidated()) {
            throw new IllegalStateException("SG-782: contraste nao validado nos temas do tenant");
        }
        if (!checklist.reduceMotionRespected()) {
            throw new IllegalStateException("SG-782: reducao de movimento do aparelho nao respeitada");
        }
        if (!checklist.axeSeriousOrCriticalZero()) {
            throw new IllegalStateException("SG-782: axe com violacao seria ou critica");
        }
    }
}
