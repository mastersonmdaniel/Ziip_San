package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.api.ApiException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo anual do SINISA assistido de um tenant (P4.2, ADR-0004: modo assistido —
 * o Ziip SAN prepara, o cliente envia no sistema oficial).
 *
 * <p>Etapas registradas do ciclo, na ordem, espelhando o ciclo oficial da
 * Portaria MCID 1.069/2025 (coleta; versao preliminar; resposta a versao
 * preliminar; publicacao e certificado):
 * <ol>
 *   <li>{@code RASCUNHO}: rascunho pre-preenchido da origem do mapa; valores
 *       manuais entram so com justificativa ({@link SinisaValue});</li>
 *   <li>{@code PRELIMINAR}: versao preliminar registrada — exige todas as
 *       variaveis do catalogo com valor (pendencia nao avanca em silencio);</li>
 *   <li>{@code RESPOSTA_REGISTRADA}: resposta a versao preliminar registrada,
 *       com o responsavel e a observacao da manifestacao;</li>
 *   <li>{@code CONCLUIDO}: ciclo fechado com o {@link ComplianceCertificate}
 *       registrado (com data).</li>
 * </ol>
 * Cada transicao e registrada em trilha append-only (ator, instante, etapa de
 * origem e destino, observacao). Correcao depois da preliminar so por reabertura
 * para o rascunho, com motivo obrigatorio registrado — nunca por edicao muda.
 *
 * <p>Guardas da abertura (aceite e fontes oficiais):
 * <ul>
 *   <li>escopo ({@code sinisa.provider_scope}) compativel com os municipios
 *       atendidos: LOCAL = 1 municipio; REGIONAL = mais de um (Area do
 *       Prestador/SINISA);</li>
 *   <li>responsavel pelo ciclo obrigatorio (o aceite exige o escopo "com
 *       responsavel");</li>
 *   <li>primeiro envio assistido e o do primeiro ano-base <b>depois</b> do corte
 *       da LC.13: ano-base menor ou igual ao ano-base de corte e recusado. O ano
 *       de corte vem da LC.13 (parametro da abertura), nao e constante aqui.</li>
 * </ul>
 *
 * <p>Persistencia: a P4.2 nao tem migration reservada no plano (mapa de
 * migrations, sec. 3), logo o ciclo e um agregado de dominio puro; quem o grava
 * implementa a persistencia quando a faixa do modulo receber migration (a
 * fiacao segue o padrao do pacote M7 da integracao). Toda a regra verificavel
 * do aceite vive aqui e nos testes, sem depender de banco.
 */
public final class SinisaCycle {

    /** Etapas do ciclo, na ordem oficial. */
    public enum Stage {
        RASCUNHO,
        PRELIMINAR,
        RESPOSTA_REGISTRADA,
        CONCLUIDO
    }

    /** Uma transicao registrada na trilha do ciclo. */
    public record Transition(Stage from, Stage to, UUID actorId, OffsetDateTime at, String note) {
        public Transition {
            Objects.requireNonNull(from, "Transicao sem etapa de origem");
            Objects.requireNonNull(to, "Transicao sem etapa de destino");
            Objects.requireNonNull(actorId, "Transicao sem ator");
            Objects.requireNonNull(at, "Transicao sem instante");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final int baseYear;
    private final SinisaProviderScope scope;
    private final UUID responsibleId;
    private final String responsibleName;
    private final int municipalitiesServed;
    private final SinisaCatalog catalog;
    private final Map<String, SinisaValue> values = new LinkedHashMap<>();
    private final List<Transition> transitions = new ArrayList<>();
    private Stage stage = Stage.RASCUNHO;
    private ComplianceCertificate certificate;

    private SinisaCycle(UUID id, UUID tenantId, int baseYear, SinisaProviderScope scope,
                        UUID responsibleId, String responsibleName, int municipalitiesServed,
                        SinisaCatalog catalog) {
        this.id = id;
        this.tenantId = tenantId;
        this.baseYear = baseYear;
        this.scope = scope;
        this.responsibleId = responsibleId;
        this.responsibleName = responsibleName;
        this.municipalitiesServed = municipalitiesServed;
        this.catalog = catalog;
    }

    /**
     * Abre o ciclo do ano-base. Recusa: escopo incompativel com os municipios,
     * responsavel ausente e ano-base que nao seja posterior ao corte da LC.13.
     *
     * @param cutoverBaseYear ano-base do corte (LC.13): o primeiro ano assistido
     *                        e {@code cutoverBaseYear + 1}
     */
    public static SinisaCycle open(UUID tenantId, int baseYear, SinisaParameters parameters,
                                   UUID responsibleId, String responsibleName,
                                   int municipalitiesServed, int cutoverBaseYear,
                                   SinisaCatalog catalog) {
        Objects.requireNonNull(tenantId, "Ciclo SINISA sem tenant");
        Objects.requireNonNull(parameters, "Ciclo SINISA sem parametros do tenant");
        Objects.requireNonNull(catalog, "Ciclo SINISA sem catalogo de variaveis");
        SinisaProviderScope scope = parameters.providerScope();
        if (responsibleId == null || responsibleName == null || responsibleName.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("responsible",
                    "sinisa-responsible-required",
                    "Ciclo SINISA exige responsavel (parametro sinisa.provider_scope com responsavel)."));
        }
        if (municipalitiesServed < 1) {
            throw ApiException.Validation.of(new ApiException.FieldError("municipalitiesServed",
                    "sinisa-municipalities-invalid",
                    "Ciclo SINISA exige ao menos 1 municipio atendido; recebido: " + municipalitiesServed + "."));
        }
        if (!scope.aceitaMunicipios(municipalitiesServed)) {
            throw new ApiException.Conflict("sinisa-scope-mismatch",
                    "Escopo " + scope + " incompativel com " + municipalitiesServed
                            + " municipio(s) atendido(s): LOCAL atende somente 1 municipio e REGIONAL "
                            + "atende mais de um (Area do Prestador do SINISA). Confira sinisa.provider_scope.");
        }
        if (baseYear <= cutoverBaseYear) {
            throw new ApiException.Conflict("sinisa-base-year-before-cutover",
                    "Ano-base " + baseYear + " nao e assistido: o primeiro envio assistido com dado do "
                            + "Ziip SAN e o do primeiro ano-base depois do corte (LC.13, ano-base de corte "
                            + cutoverBaseYear + ").");
        }
        return new SinisaCycle(UUID.randomUUID(), tenantId, baseYear, scope,
                responsibleId, responsibleName, municipalitiesServed, catalog);
    }

    /**
     * Pre-preenche o rascunho a partir das origens do mapa: para cada variavel
     * automatica sem valor, consulta a porta de dados; origem sem dado deixa a
     * variavel pendente (nunca zero inventado). Variaveis ja preenchidas (manual
     * justificado incluido) nao sao sobrescritas. So no rascunho.
     *
     * @return chaves das variaveis que ficaram pendentes, na ordem do catalogo
     */
    public List<String> prefill(SinisaSourceData sourceData, UUID actor, OffsetDateTime at) {
        Objects.requireNonNull(sourceData, "Porta de dados de origem e obrigatoria");
        Objects.requireNonNull(actor, "Ator do pre-preenchimento e obrigatorio");
        Objects.requireNonNull(at, "Instante do pre-preenchimento e obrigatorio");
        requireStage(Stage.RASCUNHO, "pre-preencher");
        List<String> pending = new ArrayList<>();
        for (SinisaVariable variable : catalog.variables()) {
            if (values.containsKey(variable.key())) {
                continue;
            }
            if (!variable.automatic()) {
                pending.add(variable.key());
                continue;
            }
            Optional<java.math.BigDecimal> fetched = sourceData.fetch(tenantId, baseYear, variable);
            if (fetched.isPresent()) {
                values.put(variable.key(), SinisaValue.automatic(variable.key(), fetched.get(), actor, at));
            } else {
                pending.add(variable.key());
            }
        }
        return List.copyOf(pending);
    }

    /**
     * Registra valor manual de uma variavel do catalogo. A justificativa e
     * obrigatoria (guarda em {@link SinisaValue}); substituir um valor existente
     * tambem exige justificativa nova. So no rascunho.
     */
    public void setManualValue(String variableKey, java.math.BigDecimal value, String justification,
                               UUID authorId, OffsetDateTime at) {
        requireStage(Stage.RASCUNHO, "informar valor manual");
        catalog.require(variableKey);
        values.put(variableKey, SinisaValue.manual(variableKey, value, justification, authorId, at));
    }

    /** Chaves do catalogo ainda sem valor, na ordem do catalogo. */
    public List<String> pendingVariables() {
        List<String> pending = new ArrayList<>();
        for (SinisaVariable variable : catalog.variables()) {
            if (!values.containsKey(variable.key())) {
                pending.add(variable.key());
            }
        }
        return List.copyOf(pending);
    }

    /** Avanca o rascunho completo para a versao preliminar registrada. */
    public void advanceToPreliminary(UUID actor, OffsetDateTime at, String note) {
        requireStage(Stage.RASCUNHO, "registrar a versao preliminar");
        List<String> pending = pendingVariables();
        if (!pending.isEmpty()) {
            throw new ApiException.Conflict("sinisa-cycle-incomplete",
                    "Versao preliminar exige todas as variaveis com valor; pendentes: "
                            + String.join(", ", pending) + ".");
        }
        move(Stage.PRELIMINAR, actor, at, note);
    }

    /** Registra a resposta (manifestacao/validacao) a versao preliminar. */
    public void registerResponse(UUID actor, OffsetDateTime at, String note) {
        requireStage(Stage.PRELIMINAR, "registrar a resposta a versao preliminar");
        if (note == null || note.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("note",
                    "sinisa-response-note-required",
                    "Resposta a versao preliminar exige a observacao da manifestacao registrada."));
        }
        move(Stage.RESPOSTA_REGISTRADA, actor, at, note);
    }

    /** Reabre um ciclo em versao preliminar para correcao no rascunho, com motivo. */
    public void reopenForCorrection(UUID actor, OffsetDateTime at, String reason) {
        requireStage(Stage.PRELIMINAR, "reabrir para correcao");
        if (reason == null || reason.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("reason",
                    "sinisa-reopen-reason-required",
                    "Reabertura do ciclo SINISA exige motivo registrado."));
        }
        move(Stage.RASCUNHO, actor, at, reason);
    }

    /** Registra o Certificado de Adimplencia (com data) e conclui o ciclo. */
    public void concludeWithCertificate(ComplianceCertificate certificate, UUID actor, OffsetDateTime at) {
        requireStage(Stage.RESPOSTA_REGISTRADA, "concluir com o certificado");
        Objects.requireNonNull(certificate, "Certificado de Adimplencia e obrigatorio para concluir");
        if (certificate.baseYear() != baseYear) {
            throw new ApiException.Conflict("sinisa-certificate-year-mismatch",
                    "Certificado do ano-base " + certificate.baseYear()
                            + " nao conclui o ciclo do ano-base " + baseYear + ".");
        }
        this.certificate = certificate;
        move(Stage.CONCLUIDO, actor, at,
                "Certificado de Adimplencia registrado, emitido em " + certificate.issuedOn() + ".");
    }

    private void move(Stage to, UUID actor, OffsetDateTime at, String note) {
        Objects.requireNonNull(actor, "Ator da transicao e obrigatorio");
        Objects.requireNonNull(at, "Instante da transicao e obrigatorio");
        transitions.add(new Transition(stage, to, actor, at, note));
        stage = to;
    }

    private void requireStage(Stage expected, String action) {
        if (stage != expected) {
            throw new ApiException.Conflict("sinisa-cycle-stage",
                    "Nao e possivel " + action + ": o ciclo esta em " + stage
                            + " e a etapa exigida e " + expected + ".");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public int baseYear() {
        return baseYear;
    }

    public SinisaProviderScope scope() {
        return scope;
    }

    public UUID responsibleId() {
        return responsibleId;
    }

    public String responsibleName() {
        return responsibleName;
    }

    public int municipalitiesServed() {
        return municipalitiesServed;
    }

    public Stage stage() {
        return stage;
    }

    public SinisaCatalog catalog() {
        return catalog;
    }

    /** Valores registrados, na ordem do catalogo (copia imutavel). */
    public Map<String, SinisaValue> values() {
        Map<String, SinisaValue> ordered = new LinkedHashMap<>();
        for (SinisaVariable variable : catalog.variables()) {
            SinisaValue value = values.get(variable.key());
            if (value != null) {
                ordered.put(variable.key(), value);
            }
        }
        return Map.copyOf(ordered);
    }

    public Optional<SinisaValue> value(String variableKey) {
        return Optional.ofNullable(values.get(variableKey));
    }

    /** Trilha de transicoes registradas, em ordem (copia imutavel). */
    public List<Transition> transitions() {
        return List.copyOf(transitions);
    }

    public Optional<ComplianceCertificate> certificate() {
        return Optional.ofNullable(certificate);
    }
}
