package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.commercial.billingrules.AmountLine;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.RoutePackage;
import br.com.ziipsan.commercial.billingrules.RouteTask;
import br.com.ziipsan.commercial.billingrules.TariffBracket;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Montador do pacote de rota de campo (P-03 / RG2-C-P2): monta, a partir de
 * entrada tipada explicita do chamador, o pacote que o servidor
 * envia/entrega ao aparelho —
 * nas DUAS formas que o projeto consome:
 *
 * <ol>
 *   <li>o {@link RoutePackage} real da ED-19 (ziipsan-billing-rules),
 *       construido pelo construtor dele, com as recusas dele — e o mesmo
 *       pacote que o servidor guarda para remontar a entrada e recalcular
 *       na submissao;</li>
 *   <li>o {@link RoutePackageDeliveryDto} no formato do espelho TS, para
 *       o mecanismo de envio entregar ao aparelho (bloco de emissao por
 *       tarefa incluido).</li>
 * </ol>
 *
 * <p><b>Padrao {@code FieldSnapshotProjector} (billing):</b> entrada
 * tipada explicita do chamador, sem valores de fabrica, sem leitura de
 * banco escondida, falha fechada. Por tarefa, o chamador entrega o
 * {@link FieldBillInput} base (com {@code currentReading =
 * previousReading} como marcador, como o {@link RouteTask} Java exige) e
 * o {@code barcodeValue}; ao nivel do pacote, rota/tenant/contrato/hash
 * do artefato/versoes/envio a cada N — e, so para o espelho TS, o tipo de
 * medicao, a referencia e a sequencia de cada tarefa e o retrato do
 * catalogo de ocorrencias, que sao dados do metering que o pacote Java
 * da ED-19 nao carrega.
 *
 * <p><b>Falha fechada, recusas nomeadas:</b> tarefa sem entrada base,
 * pacote sem tarefas, hash do artefato ausente, contrato ausente, envio
 * a cada N invalido — as recusas sao as dos construtores reais
 * ({@link RoutePackage}, {@link RouteTask}, {@link FieldBillInput}) e as
 * dos records de entrada abaixo. Nada e presumido nem completado.
 *
 * <p><b>Limite declarado (pendencia P-MOB-INT-01):</b> este montador NAO
 * resolve dado nenhum: quem resolve os {@code FieldBillInput} por rota
 * (tarifa vigente da unidade, debitos/creditos, vencimento) e o
 * mecanismo de ENVIO servidor -> aparelho que entrega o DTO ao aparelho
 * ainda nao existem em producao — ficam como pendencia nomeada; o
 * montador esta pronto a ser fiado quando a fonte existir.
 */
@Service
public class RoutePackageAssembler {

    /**
     * Entrada de UMA tarefa: a base de calculo da ED-19 + o que o espelho
     * TS precisa e o pacote Java nao carrega.
     *
     * @param inputBase    entrada base da tarefa (marcador
     *                     currentReading = previousReading, validado pelo
     *                     {@link RouteTask} Java na montagem)
     * @param barcodeValue valor do codigo de barras resolvido, ou null
     *                     quando a tarefa ainda nao tem cobranca resolvida
     * @param kind         tipo de medicao da unidade (metering)
     * @param reference    mes de referencia da leitura (AAAA-MM no DTO)
     * @param sequence     ordem da tarefa na rota (exibicao no aparelho)
     */
    public record TaskInput(FieldBillInput inputBase, String barcodeValue,
                            MeasurementKind kind, YearMonth reference, int sequence) {

        public TaskInput {
            Objects.requireNonNull(inputBase, "entrada base da tarefa e obrigatoria");
            Objects.requireNonNull(kind, "tipo de medicao da tarefa e obrigatorio");
            Objects.requireNonNull(reference, "referencia da tarefa e obrigatoria");
        }
    }

    /** Retrato de uma ocorrencia do catalogo, para o espelho TS. */
    public record OccurrenceInput(String code, boolean requiresPhoto, boolean requiresReading) {

        public OccurrenceInput {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Codigo da ocorrencia do pacote e obrigatorio");
            }
        }
    }

    /**
     * Entrada do pacote inteiro: o que o {@link RoutePackage} Java exige
     * mais a referencia da rota do espelho TS.
     *
     * @param routeReference referencia da rota no espelho TS (AAAA-MM)
     */
    public record PackageInput(UUID routeId, UUID tenantId, OffsetDateTime issuedAt,
                               String routeReference,
                               String rulesContract, String rulesArtifactSha256,
                               List<TaskInput> tasks,
                               List<OccurrenceInput> occurrences,
                               Map<String, String> parameterVersions,
                               Map<String, String> thermalTemplateVersions,
                               int sendEveryN) {

        public PackageInput {
            Objects.requireNonNull(routeId, "rota do pacote e obrigatoria");
            Objects.requireNonNull(tenantId, "tenant do pacote e obrigatorio");
            Objects.requireNonNull(issuedAt, "emissao do pacote e obrigatoria");
            if (routeReference == null || routeReference.isBlank()) {
                throw new IllegalArgumentException("Referencia da rota e obrigatoria no pacote");
            }
            tasks = tasks == null ? null : List.copyOf(tasks);
            occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
        }
    }

    /** As duas formas do pacote montado: a real da ED-19 e o DTO de entrega. */
    public record AssembledPackage(RoutePackage routePackage, RoutePackageDeliveryDto delivery) {
    }

    /**
     * Monta o pacote nas duas formas. As recusas dos construtores reais
     * propagam sem traducao: pacote sem tarefas, hash ausente, marcador
     * de leitura base invalido — nada sai pela metade.
     */
    public AssembledPackage assemble(PackageInput input) {
        Objects.requireNonNull(input, "entrada do pacote de rota e obrigatoria");
        if (input.tasks() == null || input.tasks().isEmpty()) {
            throw new IllegalArgumentException("Pacote da rota sem nenhuma tarefa");
        }
        List<RouteTask> routeTasks = input.tasks().stream()
                .map(t -> new RouteTask(t.inputBase(), t.barcodeValue()))
                .toList();
        RoutePackage routePackage = new RoutePackage(input.routeId(), input.tenantId(), input.issuedAt(),
                input.rulesContract(), input.rulesArtifactSha256(), routeTasks,
                input.parameterVersions(), input.thermalTemplateVersions(), input.sendEveryN());
        return new AssembledPackage(routePackage, toDeliveryDto(input));
    }

    private RoutePackageDeliveryDto toDeliveryDto(PackageInput input) {
        List<RoutePackageDeliveryDto.OccurrenceDto> occurrences = input.occurrences().stream()
                .map(o -> new RoutePackageDeliveryDto.OccurrenceDto(
                        o.code(), o.requiresPhoto(), o.requiresReading()))
                .toList();
        List<RoutePackageDeliveryDto.TaskDto> tasks = input.tasks().stream()
                .map(this::toTaskDto)
                .toList();
        return new RoutePackageDeliveryDto(input.routeId().toString(), input.routeReference(),
                input.issuedAt().toString(), occurrences, tasks);
    }

    private RoutePackageDeliveryDto.TaskDto toTaskDto(TaskInput task) {
        FieldBillInput base = task.inputBase();
        return new RoutePackageDeliveryDto.TaskDto(
                base.taskId().toString(), base.unitId().toString(), base.installationId().toString(),
                task.kind().name(), task.reference().toString(),
                base.previousReading(), base.meterDigits(), task.sequence(),
                toEmissionDto(base, task.barcodeValue()));
    }

    private RoutePackageDeliveryDto.EmissionDto toEmissionDto(FieldBillInput base, String barcodeValue) {
        FieldTariffSnapshot snapshot = base.snapshot();
        List<RoutePackageDeliveryDto.BracketDto> brackets = snapshot.brackets().stream()
                .map(this::toBracketDto)
                .toList();
        RoutePackageDeliveryDto.SnapshotDto snapshotDto = new RoutePackageDeliveryDto.SnapshotDto(
                snapshot.tariffVersionId().toString(), snapshot.categoryId().toString(),
                snapshot.minimumM3PerEconomy(), snapshot.fixedChargePerEconomy(), brackets,
                snapshot.sewerEnabled(), snapshot.sewerPct(), snapshot.parameterVersions());
        return new RoutePackageDeliveryDto.EmissionDto(snapshotDto, base.economies(),
                toAmountLines(base.debits()), toAmountLines(base.credits()),
                base.dueDate().toString(), barcodeValue);
    }

    private RoutePackageDeliveryDto.BracketDto toBracketDto(TariffBracket bracket) {
        return new RoutePackageDeliveryDto.BracketDto(bracket.pricePerM3(), bracket.widthM3());
    }

    private List<RoutePackageDeliveryDto.AmountLineDto> toAmountLines(List<AmountLine> lines) {
        return lines.stream()
                .map(l -> new RoutePackageDeliveryDto.AmountLineDto(l.amount(), l.code()))
                .toList();
    }
}
