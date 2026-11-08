package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Boletim de contingência do ciclo de leitura (ED-15): o retrato de uma
 * rota que não pôde ser concluída como planejado — quantas leituras foram
 * feitas, quantas ficaram impedidas e quantas ficaram pendentes, e por quê.
 *
 * <p>O boletim é um fato declarado, não uma estimativa: os contadores
 * precisam fechar com o planejado ({@code planned = read + impeded +
 * pending}) e, havendo pendência ou impedimento, o motivo da contingência
 * é obrigatório — boletim sem motivo esconderia a contingência que ele
 * existe para registrar. Nenhum motivo é presumido ou padronizado aqui:
 * quem emite informa o texto.
 */
public record ContingencyBulletin(
        UUID routeId,
        LocalDate date,
        int planned,
        int read,
        int impeded,
        List<UUID> pendingUnitIds,
        String reason) {

    public ContingencyBulletin {
        if (routeId == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "boletim de contingência sem rota");
        }
        if (date == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "boletim de contingência sem data");
        }
        if (planned < 0 || read < 0 || impeded < 0) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "contadores do boletim não podem ser negativos");
        }
        pendingUnitIds = pendingUnitIds == null ? List.of() : List.copyOf(pendingUnitIds);
        if (new java.util.HashSet<>(pendingUnitIds).size() != pendingUnitIds.size()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "a lista de unidades pendentes do boletim tem unidade duplicada: "
                            + "a mesma pendência seria contada duas vezes");
        }
        if (planned != read + impeded + pendingUnitIds.size()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "os contadores do boletim não fecham: planejado=" + planned
                            + ", lidas=" + read + ", impedidas=" + impeded
                            + ", pendentes=" + pendingUnitIds.size());
        }
        boolean hasContingency = impeded > 0 || !pendingUnitIds.isEmpty();
        if (hasContingency && (reason == null || reason.isBlank())) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "boletim com impedimento ou pendência exige o motivo da contingência");
        }
    }

    /** O boletim é de contingência (houve impedimento ou pendência). */
    public boolean isContingency() {
        return impeded > 0 || !pendingUnitIds.isEmpty();
    }

    /** Quantas leituras ficaram pendentes. */
    public int pending() {
        return pendingUnitIds.size();
    }
}
