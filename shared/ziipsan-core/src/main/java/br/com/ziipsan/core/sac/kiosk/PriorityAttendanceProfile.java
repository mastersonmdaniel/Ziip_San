package br.com.ziipsan.core.sac.kiosk;

import java.util.Objects;

/**
 * Perfil de atendimento prioritario e de acessibilidade da P1.3 (CAD-81),
 * CONSUMIDO pela ED-40 — nao redefinido aqui alem do recorte que a senha
 * presencial precisa carregar. Categorias da Lei 10.048/2000, art. 1o
 * (pessoas com deficiencia, idosos com 60 anos ou mais, gestantes,
 * lactantes, pessoas com criancas de colo e obesos), com os formatos
 * acessiveis e canais da Lei 13.146/2015, art. 62, ja previstos na P1.3
 * (braile, fonte ampliada, PDF acessivel, audio; Libras, TDD,
 * videoatendimento) e o acompanhante registrado no cadastro.
 */
public record PriorityAttendanceProfile(PriorityCategory category, boolean companionPresent,
                                        AccessibleFormat accessibleFormat,
                                        AssistanceChannel assistanceChannel) {

    public enum PriorityCategory {
        NONE, PERSON_WITH_DISABILITY, ELDERLY_60_OR_OVER, PREGNANT,
        NURSING, WITH_CHILD_IN_ARMS, WITH_OBESITY
    }

    public enum AccessibleFormat { NONE, BRAILLE, LARGE_PRINT, ACCESSIBLE_PDF, AUDIO }

    public enum AssistanceChannel { NONE, LIBRAS, TDD, VIDEO_SERVICE }

    public PriorityAttendanceProfile {
        Objects.requireNonNull(category, "categoria do perfil obrigatoria");
        Objects.requireNonNull(accessibleFormat, "formato acessivel obrigatorio");
        Objects.requireNonNull(assistanceChannel, "canal de atendimento obrigatorio");
    }

    public static PriorityAttendanceProfile common() {
        return new PriorityAttendanceProfile(PriorityCategory.NONE, false,
                AccessibleFormat.NONE, AssistanceChannel.NONE);
    }

    public static PriorityAttendanceProfile priority(PriorityCategory category) {
        if (category == null || category == PriorityCategory.NONE) {
            throw new IllegalArgumentException("categoria prioritaria obrigatoria");
        }
        return new PriorityAttendanceProfile(category, false,
                AccessibleFormat.NONE, AssistanceChannel.NONE);
    }

    /** Prioridade LEGAL (Lei 10.048/2000): quem tem, nao espera atras de quem nao tem. */
    public boolean legalPriority() {
        return category != PriorityCategory.NONE;
    }

    public boolean needsAccessibilitySupport() {
        return accessibleFormat != AccessibleFormat.NONE
                || assistanceChannel != AssistanceChannel.NONE;
    }
}
