package br.com.ziipsan.commercial.billing.crisis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Plano de rodizio por setor ou zona de abastecimento (OPE-10): os setores
 * sao os setores de manobra/abastecimento da ED-54 (codigos referenciados,
 * nao redefinidos). Calendario de desligamentos por setor; as horas sem
 * agua por setor sao a ENTRADA da intermitencia lida pela ocorrencia de
 * rede da ED-92 (metodo hoursWithoutWater).
 *
 * Comunicacao aos afetados: SO pelos canais existentes - atendimento da
 * ED-35 e aviso da ED-54/ED-41 - via portas de CrisisApplicationService.
 * Nenhum canal novo e criado aqui.
 */
public final class RotationPlan {

    public record RotationEntry(String sectorCode, LocalDateTime offStart, LocalDateTime offEnd) {
        public RotationEntry {
            if (sectorCode == null || sectorCode.isBlank())
                throw new IllegalArgumentException("setor do rodizio obrigatorio (setor da ED-54)");
            Objects.requireNonNull(offStart, "inicio obrigatorio");
            Objects.requireNonNull(offEnd, "fim obrigatorio");
            if (!offEnd.isAfter(offStart)) throw new IllegalArgumentException("janela do rodizio invertida");
        }
        public Duration duration() { return Duration.between(offStart, offEnd); }
        boolean overlaps(LocalDateTime from, LocalDateTime to) {
            return offStart.isBefore(to) && offEnd.isAfter(from);
        }
    }

    private final UUID id;
    private final Set<String> sectorCodes;
    private final List<RotationEntry> calendar = new ArrayList<>();

    public RotationPlan(UUID id, Set<String> sectorCodes, boolean rotationEnabled) {
        this.id = Objects.requireNonNull(id, "plano obrigatorio");
        if (!rotationEnabled)
            throw new IllegalStateException("rodizio desligado para o tenant (crisis.rotation_enabled)");
        if (sectorCodes == null || sectorCodes.isEmpty())
            throw new IllegalArgumentException("ao menos um setor (ED-54) e obrigatorio");
        this.sectorCodes = Set.copyOf(sectorCodes);
    }

    public RotationPlan add(RotationEntry entry) {
        Objects.requireNonNull(entry, "lancamento obrigatorio");
        if (!sectorCodes.contains(entry.sectorCode()))
            throw new IllegalArgumentException("setor fora do plano: " + entry.sectorCode());
        calendar.add(entry);
        return this;
    }

    public List<RotationEntry> calendar() { return List.copyOf(calendar); }
    public Set<String> sectorCodes() { return sectorCodes; }

    /**
     * Horas sem agua do setor no periodo: entrada da intermitencia (ED-92).
     * Correcao m1 da revisao: soma a UNIAO das janelas recortadas do setor,
     * nao a soma aritmetica - janelas sobrepostas do mesmo setor (ex.:
     * correcao de calendario) nao contam em dobro (a sonda da revisao:
     * 06h-18h + 12h-24h devolvia PT24H; a uniao real e 18h).
     */
    public Duration hoursWithoutWater(String sectorCode, LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "inicio obrigatorio");
        Objects.requireNonNull(to, "fim obrigatorio");
        List<LocalDateTime[]> clipped = new ArrayList<>();
        for (RotationEntry e : calendar) {
            if (!e.sectorCode().equals(sectorCode) || !e.overlaps(from, to)) continue;
            LocalDateTime s = e.offStart().isBefore(from) ? from : e.offStart();
            LocalDateTime f = e.offEnd().isAfter(to) ? to : e.offEnd();
            clipped.add(new LocalDateTime[]{s, f});
        }
        clipped.sort(java.util.Comparator.comparing((LocalDateTime[] iv) -> iv[0]));
        Duration total = Duration.ZERO;
        LocalDateTime mergedStart = null, mergedEnd = null;
        for (LocalDateTime[] iv : clipped) {
            if (mergedStart == null) {
                mergedStart = iv[0]; mergedEnd = iv[1];
            } else if (!iv[0].isAfter(mergedEnd)) {
                if (iv[1].isAfter(mergedEnd)) mergedEnd = iv[1];
            } else {
                total = total.plus(Duration.between(mergedStart, mergedEnd));
                mergedStart = iv[0]; mergedEnd = iv[1];
            }
        }
        if (mergedStart != null) total = total.plus(Duration.between(mergedStart, mergedEnd));
        return total;
    }
}
