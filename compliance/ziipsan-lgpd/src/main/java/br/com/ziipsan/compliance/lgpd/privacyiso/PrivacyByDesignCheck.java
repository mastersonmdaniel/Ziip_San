package br.com.ziipsan.compliance.lgpd.privacyiso;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Verificação de privacidade desde a concepção na mudança (ED-74):
 * quando uma mudança introduz tratamento NOVO ou ALTERA um existente,
 * o registro da verificação exige a referência do RIPD que cobriu o
 * tratamento — sem RIPD, a verificação não se completa e a mudança não
 * ganha o selo interno de verificada. O modelo de RIPD e o registro do
 * RIPD são da P3.10 ({@code lgpd_dpia}); aqui guarda-se só a REFERÊNCIA,
 * como a P3.10 já faz com {@code processingRecordRef} no incidente.
 *
 * <p>Mudança que NÃO mexe em tratamento dispensa o RIPD, mas exige a
 * justificativa dessa leitura: "não se aplica" sem motivo declarado é
 * exatamente o furo que a verificação existe para fechar.
 *
 * <p>Só com a norma ligada: a LGPD pede RIPD nas hipóteses dela (art. 38
 * e Res. CD/ANPD), mas a verificação SISTEMÁTICA a cada mudança de
 * tratamento é o acréscimo de gestão da 27701 — por isso o registro
 * nasce atrás da porta da ativação, sem tocar no fluxo da P3.10.
 */
public record PrivacyByDesignCheck(String changeRef, boolean newOrChangedProcessing,
                                   String dpiaRef, String rationale, UUID checkedBy,
                                   LocalDate checkedOn) {

    /** Teto das referências no SQL pendente da ED-74 (VARCHAR(200)). */
    public static final int REF_MAX = 200;

    public PrivacyByDesignCheck {
        if (changeRef == null || changeRef.isBlank() || changeRef.length() > REF_MAX) {
            throw new IllegalArgumentException("changeRef é obrigatória (identificador da "
                    + "mudança verificada: ocorrência, release ou protocolo) e tem no máximo "
                    + REF_MAX + " caracteres");
        }
        if (dpiaRef != null && dpiaRef.length() > REF_MAX) {
            throw new IllegalArgumentException("dpiaRef tem no máximo " + REF_MAX + " caracteres");
        }
        if (newOrChangedProcessing && (dpiaRef == null || dpiaRef.isBlank())) {
            throw new IllegalArgumentException("tratamento novo ou alterado exige dpiaRef: "
                    + "a verificação de privacidade desde a concepção registra que o "
                    + "tratamento teve RIPD (modelo e registro da P3.10)");
        }
        if (!newOrChangedProcessing && (rationale == null || rationale.isBlank())) {
            throw new IllegalArgumentException("mudança sem tratamento novo/alterado exige "
                    + "rationale: a leitura de não-aplicabilidade precisa de motivo declarado");
        }
        dpiaRef = dpiaRef == null ? "" : dpiaRef.trim();
        rationale = rationale == null ? "" : rationale;
        Objects.requireNonNull(checkedBy, "checkedBy é obrigatório (quem verificou)");
        Objects.requireNonNull(checkedOn, "checkedOn é obrigatório (quando verificou)");
    }

    /** Nascimento da verificação: só com a norma ligada (porta da ativação). */
    public static PrivacyByDesignCheck register(Iso27701Activation activation, String changeRef,
                                                boolean newOrChangedProcessing, String dpiaRef,
                                                String rationale, UUID checkedBy,
                                                LocalDate checkedOn) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new PrivacyByDesignCheck(changeRef, newOrChangedProcessing, dpiaRef, rationale,
                checkedBy, checkedOn);
    }

    /** A verificação está completa quando a sua exigência foi atendida no nascimento. */
    public boolean complete() {
        return newOrChangedProcessing ? !dpiaRef.isBlank() : !rationale.isBlank();
    }
}
