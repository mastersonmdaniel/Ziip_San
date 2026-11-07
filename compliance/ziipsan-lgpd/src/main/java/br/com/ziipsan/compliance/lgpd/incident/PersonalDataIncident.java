package br.com.ziipsan.compliance.lgpd.incident;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Registro do incidente com dado pessoal (P3.10), em memória — a persistência usa o que já
 * existe no módulo lgpd (schema das {@code V2_5}/{@code V2_7}); o plano NÃO reserva migration
 * nova para a P3.10 (a {@code V23_1} é da P3.40), e nenhuma foi criada nesta entrega.
 *
 * <p>Vínculos exigidos pela spec:
 * <ul>
 *   <li><b>ocorrência da P2.25</b> ({@code occurrenceId}): incidente e RIPD nascem ligados à
 *       ocorrência única (não conformidade, incidente, melhoria) do módulo certification;</li>
 *   <li><b>escopo</b> pelas categorias da classificação por coluna da P0.11 (DAD-29):
 *       {@code affectedColumns} são as colunas classificadas e {@code scope} é o conjunto
 *       das classes dessas colunas, informado por quem abre o registro a partir do
 *       catálogo {@code data_classification}; o construtor exige os dois campos não
 *       vazios, mas NÃO confere a correspondência coluna↔categoria contra o catálogo —
 *       essa conferência é da integração (leitura do catálogo pendente, ver relatório);</li>
 *   <li><b>registro de operações de tratamento</b> (P3.40, tarefa posterior):
 *       {@code processingRecordRef} é o vínculo PREPARADO — campo declarado e serializado,
 *       preenchido quando a P3.40 entregar o registro; a P3.10 não implementa a P3.40;</li>
 *   <li><b>retenção</b>: {@code retainUntil} = conhecimento + {@code ret.incident_record_years}
 *       (5 anos).</li>
 * </ul>
 *
 * <p>JSON somente pela classe {@code Json} do core (regra do projeto).
 */
public record PersonalDataIncident(
        UUID occurrenceId,
        Instant knownAt,
        Set<DataCategory> scope,
        List<String> affectedColumns,
        Instant controllerNotifiedAt,
        String processingRecordRef,
        LocalDate retainUntil) {

    public PersonalDataIncident {
        Objects.requireNonNull(occurrenceId, "occurrenceId (ocorrência da P2.25) é obrigatório");
        Objects.requireNonNull(knownAt, "knownAt é obrigatório: o relógio começa no conhecimento");
        if (scope == null || scope.isEmpty()) {
            throw new IllegalArgumentException(
                    "Escopo do incidente é obrigatório: derive das categorias da classificação "
                            + "por coluna da P0.11 (DAD-29)");
        }
        if (affectedColumns == null || affectedColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "affectedColumns é obrigatório: o escopo se prova pelas colunas classificadas (DAD-29)");
        }
        Objects.requireNonNull(retainUntil, "retainUntil é obrigatório (ret.incident_record_years)");
        scope = Set.copyOf(scope);
        affectedColumns = List.copyOf(affectedColumns);
    }

    /** Fábrica: monta o registro com a retenção de {@code ret.incident_record_years} aplicada. */
    public static PersonalDataIncident open(UUID occurrenceId, IncidentClock clock,
                                            Set<DataCategory> scope, List<String> affectedColumns,
                                            int incidentRecordYears, String processingRecordRef) {
        Objects.requireNonNull(clock, "clock é obrigatório");
        LgpdIncidentParameters.validateIncidentRecordYears(incidentRecordYears);
        LocalDate retainUntil = LocalDate.ofInstant(clock.knownAt(), java.time.ZoneOffset.UTC)
                .plusYears(incidentRecordYears);
        return new PersonalDataIncident(occurrenceId, clock.knownAt(), scope, affectedColumns,
                null, processingRecordRef, retainUntil);
    }

    /**
     * Registra o aviso ao controlador, devolvendo uma CÓPIA do registro com
     * {@code controllerNotifiedAt} preenchido (o registro original não muda: o record é
     * imutável). É esse campo que o procedimento §3 manda consultar para suprimir os
     * alertas da Central depois do aviso e para medir o atraso contra
     * {@link IncidentClock#operatorNoticeDeadline()}.
     */
    public PersonalDataIncident withControllerNotifiedAt(Instant when) {
        Objects.requireNonNull(when, "controllerNotifiedAt é obrigatório ao registrar o aviso");
        return new PersonalDataIncident(occurrenceId, knownAt, scope, affectedColumns, when,
                processingRecordRef, retainUntil);
    }

    /** JSON do registro, só pela classe {@code Json} do core. */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("occurrenceId", occurrenceId.toString());
        map.put("knownAt", knownAt.toString());
        map.put("scope", scope.stream().map(Enum::name).sorted().toList());
        map.put("affectedColumns", affectedColumns);
        map.put("controllerNotifiedAt", controllerNotifiedAt == null ? null : controllerNotifiedAt.toString());
        map.put("processingRecordRef", processingRecordRef);
        map.put("retainUntil", retainUntil.toString());
        return Json.write(map);
    }
}
