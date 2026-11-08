package br.com.ziipsan.intelligence.losses.dmc;

import br.com.ziipsan.core.incident.NetworkIncident;

import java.util.List;
import java.util.Objects;

/**
 * Perdas reais vindas das ocorrencias da ED-92 (ED-85; JOR-33): o
 * volume perdido e o tempo de reparo gravados no encerramento da OS
 * de reparo de CADA ocorrencia ({@code NetworkIncident.ClosureData})
 * sao consumidos DA ENTIDADE da ED-92 — nenhuma ocorrencia paralela e
 * criada no losses e nenhum valor e recapturado. Desconhecido (nulo
 * na OS) permanece AUSENTE (null), nunca zero — mesma correcao M2 da
 * revisao da ED-92; os contadores de conhecidos acompanham o total.
 *
 * <p>No balanco, este anexo e EXPLICATIVO: a perda real do balanco e
 * o residuo (entrada liquida menos consumo autorizado menos perdas
 * aparentes); a soma das ocorrencias tem de caber nela — o
 * {@link WaterBalanceCalculator} sinaliza quando nao cabe.
 */
public record IncidentLossInputs(long closedIncidents, long volumeKnown,
                                 Double totalLostVolumeM3OrNull, long repairKnown,
                                 Double avgRepairMinutesOrNull) {

    public static IncidentLossInputs fromIncidents(List<NetworkIncident> incidents) {
        Objects.requireNonNull(incidents, "ocorrencias da ED-92 obrigatorias");
        long closed = 0, volKnown = 0, repKnown = 0;
        double volSum = 0.0, repSum = 0.0;
        for (NetworkIncident incident : incidents) {
            if (incident.status() != NetworkIncident.Status.CLOSED || incident.closure() == null) continue;
            closed++;
            if (incident.closure().lostVolumeM3OrNull() != null) {
                volKnown++; volSum += incident.closure().lostVolumeM3OrNull();
            }
            if (incident.closure().repairMinutesOrNull() != null) {
                repKnown++; repSum += incident.closure().repairMinutesOrNull();
            }
        }
        return new IncidentLossInputs(closed, volKnown,
                volKnown == 0 ? null : volSum,
                repKnown,
                repKnown == 0 ? null : repSum / repKnown);
    }

    public static IncidentLossInputs none() { return new IncidentLossInputs(0, 0, null, 0, null); }
}
