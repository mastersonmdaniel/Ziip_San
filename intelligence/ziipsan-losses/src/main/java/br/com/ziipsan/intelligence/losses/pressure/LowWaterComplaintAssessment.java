package br.com.ziipsan.intelligence.losses.pressure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vinculo com reclamacao de pouca agua (ED-86, OPE-04): a reclamacao
 * do atendimento (protocolo do SAC, ED-35) e confrontada com a serie
 * de pressao do ponto de controle da zona na janela em torno da
 * reclamacao — a selecao da janela e do chamador/fiacao; aqui entram
 * as amostras ja selecionadas. Desfecho:
 * <ul>
 *   <li>{@code CORROBORATED_LOW_PRESSURE} — alguma amostra abaixo de
 *       {@code pressure.min_mca} na janela;</li>
 *   <li>{@code NOT_CORROBORATED} — ha medicao e nenhuma abaixo do
 *       minimo;</li>
 *   <li>{@code NO_DATA} — sem medicao na janela: a ausencia fica
 *       registrada como ausencia, nunca como "sem problema".</li>
 * </ul>
 * O protocolo e referenciado pelo codigo (sem FK entre modulos,
 * padrao ED-85/ED-92); a reclamacao em si continua sendo do SAC.
 *
 * <p>Par zona x ponto (correcao D2 da revisao da ED-86): o
 * {@link ComplaintRef} carrega {@code zoneId} e {@code pointCode}
 * como referencias soltas porque a serie chega ja selecionada pelo
 * chamador; a amarracao do par e da PERSISTENCIA — a tabela
 * {@code low_water_complaint_links} liga o ponto por FK composta
 * {@code (tenant_id, zone_id, point_id)} contra a zona do proprio
 * ponto, e o banco recusa zone_id divergente da zona do ponto. A
 * fiacao (P-ED86-01) deve resolver o ponto dentro da zona indicada.
 */
public final class LowWaterComplaintAssessment {

    public record ComplaintRef(String protocolRef, UUID zoneId, String pointCode,
                               LocalDateTime complainedAt) {
        public ComplaintRef {
            if (protocolRef == null || protocolRef.isBlank()) throw new IllegalArgumentException(
                    "protocolo da reclamacao de pouca agua obrigatorio (OPE-04)");
            Objects.requireNonNull(zoneId, "reclamacao exige a zona de pressao");
            if (pointCode == null || pointCode.isBlank()) throw new IllegalArgumentException(
                    "ponto de controle da reclamacao obrigatorio");
            Objects.requireNonNull(complainedAt, "instante da reclamacao obrigatorio");
        }
    }

    public enum Outcome { CORROBORATED_LOW_PRESSURE, NOT_CORROBORATED, NO_DATA }

    public record Assessment(ComplaintRef complaint, Outcome outcome,
                             Double lowestObservedMcaOrNull, int samples, int lowSamples) {}

    private LowWaterComplaintAssessment() {}

    public static Assessment assess(ComplaintRef complaint,
                                    List<PressureSeriesPort.PressureSample> seriesInWindow,
                                    PressureParameters.ZoneLimits limits) {
        Objects.requireNonNull(complaint, "reclamacao obrigatoria");
        Objects.requireNonNull(limits, "limites da zona obrigatorios (pressure.min_mca)");
        if (seriesInWindow == null || seriesInWindow.isEmpty())
            return new Assessment(complaint, Outcome.NO_DATA, null, 0, 0);
        double lowest = seriesInWindow.stream()
                .mapToDouble(PressureSeriesPort.PressureSample::pressureMca).min().orElseThrow();
        int low = (int) seriesInWindow.stream()
                .filter(s -> limits.isBelow(s.pressureMca())).count();
        Outcome outcome = low > 0 ? Outcome.CORROBORATED_LOW_PRESSURE : Outcome.NOT_CORROBORATED;
        return new Assessment(complaint, outcome, lowest, seriesInWindow.size(), low);
    }
}
