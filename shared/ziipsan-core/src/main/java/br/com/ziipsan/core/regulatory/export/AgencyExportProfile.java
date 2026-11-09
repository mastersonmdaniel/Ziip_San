package br.com.ziipsan.core.regulatory.export;

import java.util.List;
import java.util.Objects;

/**
 * Perfil de exportacao parametrizado por agencia (ED-97, RAS-01/RAS-08/
 * RAS-31), construido SOBRE o perfil regulatorio da P2.31: leiaute,
 * periodicidade, canal, versao do leiaute e a fonte normativa.
 *
 * <p>Modo assistido do ADR-0004: o canal e sempre arquivo gerado pelo
 * sistema e enviado pelo tenant ({@link Channel#ASSISTED_FILE}) — o
 * sistema nao transmite a agencia. O parametro regente e
 * {@code reg.reports} (objeto, classe d, REG, conforme o aceite).</p>
 *
 * <p>{@code layoutComplete=false} declara, sem fingir, que o leiaute
 * oficial nao foi localizado na fonte: a validacao pre-envio entao exige
 * aceite expresso do tenant dos valores proprios (RAS-31).</p>
 */
public record AgencyExportProfile(
        String agencyCode, String profileKey, String layoutId, String layoutVersion,
        Periodicity periodicity, Channel channel, boolean factory,
        String sourceAct, List<LayoutField> layoutFields, boolean layoutComplete) {

    public enum Periodicity { MONTHLY, QUARTERLY, ANNUAL }
    public enum Channel { ASSISTED_FILE }

    /** Parametro regente nomeado pelo aceite da ED-97 (classe d, REG). */
    public static final String REPORTS_PARAM = "reg.reports";
    /** Parametro do relatorio anual de tarifa social (CAD-92; catalogo de parametros). */
    public static final String SOCIAL_ANNUAL_REPORT_PARAM = "social.annual_report";

    public AgencyExportProfile {
        Objects.requireNonNull(agencyCode, "agencyCode");
        Objects.requireNonNull(profileKey, "profileKey");
        Objects.requireNonNull(layoutId, "layoutId");
        Objects.requireNonNull(layoutVersion, "layoutVersion");
        Objects.requireNonNull(periodicity, "periodicity");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(sourceAct, "sourceAct");
        layoutFields = layoutFields == null ? List.of() : List.copyOf(layoutFields);
        if (agencyCode.isBlank() || profileKey.isBlank() || layoutId.isBlank()
                || layoutVersion.isBlank() || sourceAct.isBlank()) {
            throw new IllegalArgumentException("perfil de exportacao incompleto");
        }
        boolean anySemFonte = layoutFields.stream()
                .anyMatch(f -> f.sourceStatus() == LayoutField.SourceStatus.SEM_FONTE);
        if (layoutComplete && anySemFonte) {
            throw new IllegalArgumentException("leiaute com campo SEM_FONTE nao pode ser declarado completo: " + profileKey);
        }
        // Correcao D5 (REVISAO-ED-97): leiaute completo exige ao menos um
        // campo — lista vazia nao pode nascer layoutComplete=true (a
        // validacao pre-envio de leiaute completo aceitaria qualquer
        // pacote nao vazio desse perfil).
        if (layoutComplete && layoutFields.isEmpty()) {
            throw new IllegalArgumentException("leiaute completo exige ao menos um campo: " + profileKey);
        }
    }

    public boolean hasSemFonteFields() {
        return layoutFields.stream()
                .anyMatch(f -> f.sourceStatus() == LayoutField.SourceStatus.SEM_FONTE);
    }

    public List<String> requiredFieldNames() {
        return layoutFields.stream().filter(LayoutField::required).map(LayoutField::name).toList();
    }
}
