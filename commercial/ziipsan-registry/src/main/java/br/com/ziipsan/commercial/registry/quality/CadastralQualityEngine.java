package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.BlockFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.CustomerFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.DeathAlertFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.EconomyFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.LinkFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.MeterFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.ReadingFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.RouteFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UndeliveredFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Motor puro das regras continuas de qualidade cadastral (P3.32, CAD-98):
 * recebe a fotografia do cadastro ({@link CadastralSnapshot}), os parametros
 * do tenant ({@link QualityParameters}, {@code quality.rules} classe d) e a
 * data de referencia, e devolve os achados em ordem deterministica e a lista
 * de regras nao executadas (desligada pelo tenant ou sem o limiar de que
 * precisa — nunca presumido).
 *
 * <p>O motor NAO escreve em tabela nenhuma do cadastro: a saida dele e o
 * achado, que entra na fila de correcao e so vira correcao como efeito de
 * OS de lote (ADR-0039). Nao ha porta de escrita de cadastro nesta classe
 * nem no pacote — o teste de arquitetura da entrega varre o pacote por
 * referencias a servicos/repositorios de escrita do cadastro.
 *
 * <p>Recortes declarados (interpretacao do aceite, registrados no relatorio):
 * <ul>
 *   <li>regras por unidade de medicao (sem hidrometro, sem leitura, sem
 *       usuario) julgam so unidades ACTIVE — unidade PROVISIONAL recem
 *       criada ainda esta em formacao (ver ocupacao UNKNOWN da P1.41);</li>
 *   <li>categoria x CNAE: julga quando a unidade tem CNAE de 7 digitos e a
 *       divisao tem politica em {@code cnaeCompatibility}; a categoria
 *       principal e a classe regulatoria com mais economias vigentes
 *       (desempate alfabetico, deterministico);</li>
 *   <li>economias x area: maximo de economias =
 *       {@code max(1, piso(area / area minima da classe))}; sem area ou sem
 *       limiar da classe, a unidade nao e julgada;</li>
 *   <li>hidrometro vencido: EXATAMENTE como a ED-14 calcula — proxima
 *       verificacao = ultima verificacao registrada + intervalo do tenant
 *       (o {@code VerificationSchedule.nextVerificationOn} dela), e vencido
 *       quando a proxima data nao e posterior a referencia (o {@code isDue}
 *       dela). Sem verificacao registrada a ED-14 RECUSA o calculo ("sem
 *       verificacao registrada nao ha proxima data calculavel") e esta
 *       regra tambem nao julga o hidrometro: NAO ha fallback para data de
 *       instalacao nem para o ano de fabricacao (uma versao anterior desta
 *       entrega atribuia essa precedencia a ED-14, que nao a contem —
 *       defeito D3 da revisao independente, corrigido alinhando a ela);</li>
 *   <li>fora do setor: setor da quadra da unidade diferente do setor da
 *       rota de leitura dela — a contencao poligono x ponto e do GIS
 *       (ED-53+), nao do registry;</li>
 *   <li>possivelmente falecido: um achado por titular, pelo sinal mais
 *       forte (validacao externa DECEASED; alerta de obito PENDING_REVIEW
 *       da P1.41; idade no limiar do tenant). Alerta CONFIRMED nao gera
 *       achado: a sucessao ja foi tratada pela P1.41;</li>
 *   <li>conta nao entregue: um achado por unidade, agregando os retornos
 *       da janela do tenant, com o motivo/origem mais recentes e a marca de
 *       endereco nao verificado quando algum motivo e
 *       ENDERECO_NAO_LOCALIZADO (a semantica de
 *       {@code UndeliveredReason.impliesUnverifiedAddress} da P2.8).</li>
 * </ul>
 */
public final class CadastralQualityEngine {

    /** Motivo de regra nao executada na varredura. */
    public enum SkipReason { DISABLED, UNCONFIGURED }

    /** Regra nao executada, com o motivo — registrado na varredura, nunca em silencio. */
    public record SkippedRule(QualityRuleCode code, SkipReason reason) {
    }

    /** Resultado da varredura: achados ordenados + regras nao executadas. */
    public record EvaluationResult(List<QualityFinding> findings, List<SkippedRule> skipped) {
        public EvaluationResult {
            findings = List.copyOf(findings);
            skipped = List.copyOf(skipped);
        }
    }

    /** Chave do motivo da P2.8 que implica endereco nao verificado (catalogo v1 da P2.8). */
    static final String REASON_ADDRESS_NOT_FOUND = "ENDERECO_NAO_LOCALIZADO";

    public EvaluationResult evaluate(CadastralSnapshot snapshot, QualityParameters params,
                                     LocalDate referenceDate) {
        if (snapshot == null || params == null || referenceDate == null) {
            throw new IllegalArgumentException("snapshot, parametros e data de referencia sao obrigatorios");
        }
        List<QualityFinding> findings = new ArrayList<>();
        List<SkippedRule> skipped = new ArrayList<>();

        evaluateDocuments(snapshot, params, findings, skipped);
        evaluateContacts(snapshot, params, referenceDate, findings, skipped);
        evaluateCategoryCnae(snapshot, params, findings, skipped);
        evaluateEconomiesArea(snapshot, params, findings, skipped);
        evaluateMetersAndReadings(snapshot, params, referenceDate, findings, skipped);
        evaluateAddresses(snapshot, params, findings, skipped);
        evaluatePossiblyDeceased(snapshot, params, referenceDate, findings, skipped);
        evaluateDuplicates(snapshot, params, findings, skipped);
        evaluateUnitWithoutUser(snapshot, params, findings, skipped);
        evaluateUndelivered(snapshot, params, referenceDate, findings, skipped);

        findings.sort(Comparator
                .comparing((QualityFinding f) -> f.ruleCode().name())
                .thenComparing(f -> f.unitId() == null ? "" : f.unitId().toString())
                .thenComparing(f -> f.customerId() == null ? "" : f.customerId().toString())
                .thenComparing(QualityFinding::detail));
        return new EvaluationResult(findings, skipped);
    }

    // ------------------------------------------------------------------
    // Documento: ausente, invalido ou com situacao irregular
    // ------------------------------------------------------------------

    private void evaluateDocuments(CadastralSnapshot snapshot, QualityParameters params,
                                   List<QualityFinding> findings, List<SkippedRule> skipped) {
        // Cada estado do documento e uma regra: a desligada e nomeada nos
        // ignorados mesmo quando as irmaes rodam.
        if (!params.ruleEnabled(QualityRuleCode.DOCUMENT_MISSING)) {
            skipped.add(new SkippedRule(QualityRuleCode.DOCUMENT_MISSING, SkipReason.DISABLED));
        }
        if (!params.ruleEnabled(QualityRuleCode.DOCUMENT_INVALID)) {
            skipped.add(new SkippedRule(QualityRuleCode.DOCUMENT_INVALID, SkipReason.DISABLED));
        }
        if (!params.ruleEnabled(QualityRuleCode.DOCUMENT_IRREGULAR)) {
            skipped.add(new SkippedRule(QualityRuleCode.DOCUMENT_IRREGULAR, SkipReason.DISABLED));
        }
        if (!params.ruleEnabled(QualityRuleCode.DOCUMENT_MISSING)
                && !params.ruleEnabled(QualityRuleCode.DOCUMENT_INVALID)
                && !params.ruleEnabled(QualityRuleCode.DOCUMENT_IRREGULAR)) {
            return;
        }
        for (CustomerFact customer : snapshot.customers()) {
            if (customer.deleted()) {
                continue;
            }
            if (!customer.documentPresent()) {
                if (!customer.documentExempt()
                        && params.ruleEnabled(QualityRuleCode.DOCUMENT_MISSING)) {
                    findings.add(finding(params, QualityRuleCode.DOCUMENT_MISSING, null, customer.id(),
                            "documento ausente sem motivo de dispensa por catalogo"));
                }
                continue;
            }
            if (params.ruleEnabled(QualityRuleCode.DOCUMENT_INVALID)) {
                if (Boolean.FALSE.equals(customer.checksumValid())) {
                    findings.add(finding(params, QualityRuleCode.DOCUMENT_INVALID, null, customer.id(),
                            "digito verificador recusado pelo DocumentNumber do core"));
                } else if ("NOT_FOUND".equals(customer.latestValidationResult())) {
                    findings.add(finding(params, QualityRuleCode.DOCUMENT_INVALID, null, customer.id(),
                            "validacao externa mais recente: NOT_FOUND (P1.41)"));
                }
            }
            if (params.ruleEnabled(QualityRuleCode.DOCUMENT_IRREGULAR)
                    && "IRREGULAR".equals(customer.latestValidationResult())) {
                findings.add(finding(params, QualityRuleCode.DOCUMENT_IRREGULAR, null, customer.id(),
                        "validacao externa mais recente: IRREGULAR (P1.41)"));
            }
        }
    }

    // ------------------------------------------------------------------
    // Contato nao verificado
    // ------------------------------------------------------------------

    private void evaluateContacts(CadastralSnapshot snapshot, QualityParameters params,
                                  LocalDate referenceDate, List<QualityFinding> findings,
                                  List<SkippedRule> skipped) {
        if (!params.ruleEnabled(QualityRuleCode.CONTACT_UNVERIFIED)) {
            skipped.add(new SkippedRule(QualityRuleCode.CONTACT_UNVERIFIED, SkipReason.DISABLED));
            return;
        }
        // So o TITULAR: cliente com vinculo vigente de papel USER (o
        // "usuario titular" da P1.3). OWNER/RESPONSIBLE nao sao julgados
        // por esta regra (correcao R3: antes qualquer vinculo vigente
        // bastava, julgando tambem nao-titulares).
        Set<UUID> linkedCustomers = snapshot.links().stream()
                .filter(l -> l.current() && "USER".equals(l.role()))
                .map(LinkFact::customerId)
                .collect(Collectors.toSet());
        Map<UUID, List<CadastralSnapshot.ContactFact>> contactsByCustomer = snapshot.contacts().stream()
                .collect(Collectors.groupingBy(CadastralSnapshot.ContactFact::customerId));
        for (CustomerFact customer : snapshot.customers()) {
            if (customer.deleted() || !linkedCustomers.contains(customer.id())) {
                continue;
            }
            List<CadastralSnapshot.ContactFact> contacts =
                    contactsByCustomer.getOrDefault(customer.id(), List.of());
            boolean anyVerified = contacts.stream()
                    .anyMatch(c -> c.currentOn(referenceDate) && "VERIFIED".equals(c.verificationState()));
            if (!anyVerified) {
                String detail = contacts.isEmpty()
                        ? "titular sem nenhum contato cadastrado"
                        : "titular sem contato verificado vigente (verification_state da P1.3)";
                findings.add(finding(params, QualityRuleCode.CONTACT_UNVERIFIED, null, customer.id(), detail));
            }
        }
    }

    // ------------------------------------------------------------------
    // Categoria incompativel com o CNAE
    // ------------------------------------------------------------------

    private void evaluateCategoryCnae(CadastralSnapshot snapshot, QualityParameters params,
                                      List<QualityFinding> findings, List<SkippedRule> skipped) {
        if (!params.ruleEnabled(QualityRuleCode.CATEGORY_CNAE_MISMATCH)) {
            skipped.add(new SkippedRule(QualityRuleCode.CATEGORY_CNAE_MISMATCH, SkipReason.DISABLED));
            return;
        }
        if (!params.hasCnaeCompatibility()) {
            skipped.add(new SkippedRule(QualityRuleCode.CATEGORY_CNAE_MISMATCH, SkipReason.UNCONFIGURED));
            return;
        }
        for (UnitFact unit : snapshot.units()) {
            if (unit.deleted()) {
                continue;
            }
            String cnae = unit.cnaeCode();
            if (cnae == null || !cnae.matches("[0-9]{7}")) {
                continue;
            }
            String principal = principalRegulatoryClass(snapshot, unit.id());
            if (principal == null) {
                continue;
            }
            String division = cnae.substring(0, 2);
            var allowed = params.allowedClassesForCnaeDivision(division);
            if (allowed.isPresent() && !allowed.get().contains(principal)) {
                findings.add(finding(params, QualityRuleCode.CATEGORY_CNAE_MISMATCH, unit.id(), null,
                        "CNAE " + cnae + " (divisao " + division + ") compativel com "
                                + allowed.get() + "; categoria principal da unidade: " + principal));
            }
        }
    }

    // ------------------------------------------------------------------
    // Economias incompativeis com a area construida
    // ------------------------------------------------------------------

    private void evaluateEconomiesArea(CadastralSnapshot snapshot, QualityParameters params,
                                       List<QualityFinding> findings, List<SkippedRule> skipped) {
        if (!params.ruleEnabled(QualityRuleCode.ECONOMIES_AREA_MISMATCH)) {
            skipped.add(new SkippedRule(QualityRuleCode.ECONOMIES_AREA_MISMATCH, SkipReason.DISABLED));
            return;
        }
        if (!params.hasAreaThresholds()) {
            skipped.add(new SkippedRule(QualityRuleCode.ECONOMIES_AREA_MISMATCH, SkipReason.UNCONFIGURED));
            return;
        }
        for (UnitFact unit : snapshot.units()) {
            if (unit.deleted() || unit.builtAreaM2() == null || unit.builtAreaM2().signum() <= 0) {
                continue;
            }
            int total = totalEconomies(snapshot, unit.id());
            String principal = principalRegulatoryClass(snapshot, unit.id());
            if (total <= 0 || principal == null) {
                continue;
            }
            var minArea = params.minAreaPerEconomyM2(principal);
            if (minArea.isEmpty()) {
                continue;
            }
            int maxEconomies = Math.max(1,
                    unit.builtAreaM2().divide(minArea.get(), 0, RoundingMode.FLOOR).intValue());
            if (total > maxEconomies) {
                findings.add(finding(params, QualityRuleCode.ECONOMIES_AREA_MISMATCH, unit.id(), null,
                        "economias vigentes " + total + " acima do maximo " + maxEconomies
                                + " para area construida " + unit.builtAreaM2().toPlainString()
                                + " m2 e area minima " + minArea.get().toPlainString()
                                + " m2/economia (" + principal + ")"));
            }
        }
    }

    // ------------------------------------------------------------------
    // Unidade ativa sem hidrometro, sem leitura em N ciclos, hidrometro vencido
    // ------------------------------------------------------------------

    private void evaluateMetersAndReadings(CadastralSnapshot snapshot, QualityParameters params,
                                           LocalDate referenceDate, List<QualityFinding> findings,
                                           List<SkippedRule> skipped) {
        Map<UUID, MeterFact> meterByUnit = new LinkedHashMap<>();
        for (MeterFact meter : snapshot.meters()) {
            meterByUnit.putIfAbsent(meter.unitId(), meter);
        }
        Map<UUID, ReadingFact> readingByUnit = snapshot.readings().stream()
                .collect(Collectors.toMap(ReadingFact::unitId, r -> r, (a, b) -> a));
        Map<UUID, UnitFact> unitById = snapshot.units().stream()
                .collect(Collectors.toMap(UnitFact::id, u -> u, (a, b) -> a));

        if (params.ruleEnabled(QualityRuleCode.ACTIVE_UNIT_WITHOUT_METER)) {
            for (UnitFact unit : snapshot.units()) {
                if (!unit.deleted() && unit.active() && !meterByUnit.containsKey(unit.id())) {
                    findings.add(finding(params, QualityRuleCode.ACTIVE_UNIT_WITHOUT_METER,
                            unit.id(), null, "unidade ativa sem hidrometro vigente instalado"));
                }
            }
        } else {
            skipped.add(new SkippedRule(QualityRuleCode.ACTIVE_UNIT_WITHOUT_METER, SkipReason.DISABLED));
        }

        if (!params.ruleEnabled(QualityRuleCode.UNIT_WITHOUT_READING)) {
            skipped.add(new SkippedRule(QualityRuleCode.UNIT_WITHOUT_READING, SkipReason.DISABLED));
        } else {
            OptionalInt cycles = params.cyclesWithoutReading();
            if (cycles.isEmpty()) {
                skipped.add(new SkippedRule(QualityRuleCode.UNIT_WITHOUT_READING, SkipReason.UNCONFIGURED));
            } else {
                for (UnitFact unit : snapshot.units()) {
                    if (unit.deleted() || !unit.active() || !meterByUnit.containsKey(unit.id())) {
                        continue;
                    }
                    ReadingFact reading = readingByUnit.get(unit.id());
                    if (reading != null
                            && reading.consecutiveCyclesWithoutReading() >= cycles.getAsInt()) {
                        findings.add(finding(params, QualityRuleCode.UNIT_WITHOUT_READING,
                                unit.id(), null,
                                "sem leitura ha " + reading.consecutiveCyclesWithoutReading()
                                        + " ciclos (limiar do tenant: " + cycles.getAsInt() + ")"));
                    }
                }
            }
        }

        if (!params.ruleEnabled(QualityRuleCode.METER_VERIFICATION_EXPIRED)) {
            skipped.add(new SkippedRule(QualityRuleCode.METER_VERIFICATION_EXPIRED, SkipReason.DISABLED));
        } else {
            OptionalInt interval = params.meterVerificationIntervalYears();
            if (interval.isEmpty()) {
                skipped.add(new SkippedRule(QualityRuleCode.METER_VERIFICATION_EXPIRED,
                        SkipReason.UNCONFIGURED));
            } else {
                for (MeterFact meter : snapshot.meters()) {
                    UnitFact unit = unitById.get(meter.unitId());
                    if (unit == null || unit.deleted()) {
                        continue;
                    }
                    // ED-14 (VerificationSchedule): sem verificacao registrada
                    // NAO ha proxima data calculavel — a regra nao julga este
                    // hidrometro (sem fallback para instalacao/fabricacao).
                    if (meter.lastVerificationOn() == null) {
                        continue;
                    }
                    LocalDate next = meter.lastVerificationOn().plusYears(interval.getAsInt());
                    if (!next.isAfter(referenceDate)) {
                        findings.add(finding(params, QualityRuleCode.METER_VERIFICATION_EXPIRED,
                                meter.unitId(), null,
                                "verificacao vencida: ultima verificacao " + meter.lastVerificationOn()
                                        + " + intervalo " + interval.getAsInt()
                                        + " anos = proxima em " + next
                                        + " (referencia " + referenceDate + ")"));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Endereco sem coordenada ou fora do setor
    // ------------------------------------------------------------------

    private void evaluateAddresses(CadastralSnapshot snapshot, QualityParameters params,
                                   List<QualityFinding> findings, List<SkippedRule> skipped) {
        Map<UUID, BlockFact> blockById = snapshot.blocks().stream()
                .collect(Collectors.toMap(BlockFact::id, b -> b, (a, b) -> a));
        Map<UUID, RouteFact> routeById = snapshot.routes().stream()
                .collect(Collectors.toMap(RouteFact::id, r -> r, (a, b) -> a));

        if (params.ruleEnabled(QualityRuleCode.ADDRESS_WITHOUT_COORDINATES)) {
            for (UnitFact unit : snapshot.units()) {
                if (unit.deleted()) {
                    continue;
                }
                boolean addressHas = Boolean.TRUE.equals(unit.addressHasCoordinates());
                if (!unit.hasCoordinates() && !addressHas) {
                    String detail = unit.addressId() == null
                            ? "unidade sem endereco ligado e sem coordenada propria"
                            : "endereco sem coordenada e unidade sem coordenada propria";
                    findings.add(finding(params, QualityRuleCode.ADDRESS_WITHOUT_COORDINATES,
                            unit.id(), null, detail));
                }
            }
        } else {
            skipped.add(new SkippedRule(QualityRuleCode.ADDRESS_WITHOUT_COORDINATES, SkipReason.DISABLED));
        }

        if (params.ruleEnabled(QualityRuleCode.ADDRESS_OUT_OF_SECTOR)) {
            for (UnitFact unit : snapshot.units()) {
                if (unit.deleted() || unit.blockId() == null || unit.readingRouteId() == null) {
                    continue;
                }
                BlockFact block = blockById.get(unit.blockId());
                RouteFact route = routeById.get(unit.readingRouteId());
                if (block == null || route == null
                        || block.sectorId() == null || route.sectorId() == null) {
                    continue;
                }
                if (!block.sectorId().equals(route.sectorId())) {
                    findings.add(finding(params, QualityRuleCode.ADDRESS_OUT_OF_SECTOR,
                            unit.id(), null,
                            "quadra no setor " + block.sectorId()
                                    + " e rota de leitura no setor " + route.sectorId()));
                }
            }
        } else {
            skipped.add(new SkippedRule(QualityRuleCode.ADDRESS_OUT_OF_SECTOR, SkipReason.DISABLED));
        }
    }

    // ------------------------------------------------------------------
    // Titular possivelmente falecido (sinal; efeito e da P1.41)
    // ------------------------------------------------------------------

    private void evaluatePossiblyDeceased(CadastralSnapshot snapshot, QualityParameters params,
                                          LocalDate referenceDate, List<QualityFinding> findings,
                                          List<SkippedRule> skipped) {
        if (!params.ruleEnabled(QualityRuleCode.HOLDER_POSSIBLY_DECEASED)) {
            skipped.add(new SkippedRule(QualityRuleCode.HOLDER_POSSIBLY_DECEASED, SkipReason.DISABLED));
            return;
        }
        Map<UUID, DeathAlertFact> pendingAlertByCustomer = new LinkedHashMap<>();
        for (DeathAlertFact alert : snapshot.deathAlerts()) {
            if ("PENDING_REVIEW".equals(alert.status())) {
                pendingAlertByCustomer.putIfAbsent(alert.customerId(), alert);
            }
        }
        OptionalInt ageThreshold = params.possiblyDeceasedAgeYears();
        for (CustomerFact customer : snapshot.customers()) {
            if (customer.deleted()) {
                continue;
            }
            if ("DECEASED".equals(customer.latestValidationResult())) {
                findings.add(finding(params, QualityRuleCode.HOLDER_POSSIBLY_DECEASED,
                        null, customer.id(),
                        "validacao externa mais recente: DECEASED (P1.41); revisao humana obrigatoria"));
                continue;
            }
            DeathAlertFact alert = pendingAlertByCustomer.get(customer.id());
            if (alert != null) {
                findings.add(finding(params, QualityRuleCode.HOLDER_POSSIBLY_DECEASED,
                        alert.unitId(), customer.id(),
                        "alerta de obito PENDING_REVIEW da P1.41 aguardando revisao humana"));
                continue;
            }
            if (ageThreshold.isPresent() && customer.birthDate() != null) {
                int age = Period.between(customer.birthDate(), referenceDate).getYears();
                if (age >= ageThreshold.getAsInt()) {
                    findings.add(finding(params, QualityRuleCode.HOLDER_POSSIBLY_DECEASED,
                            null, customer.id(),
                            "idade " + age + " anos atinge o limiar do tenant de "
                                    + ageThreshold.getAsInt() + " anos (sinal fraco, so indaga)"));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Duplicidades
    // ------------------------------------------------------------------

    private void evaluateDuplicates(CadastralSnapshot snapshot, QualityParameters params,
                                    List<QualityFinding> findings, List<SkippedRule> skipped) {
        if (params.ruleEnabled(QualityRuleCode.DUPLICATE_DOCUMENT)) {
            Map<String, List<CustomerFact>> byHash = snapshot.customers().stream()
                    .filter(c -> !c.deleted() && c.documentHash() != null && !c.documentHash().isBlank())
                    .collect(Collectors.groupingBy(CustomerFact::documentHash));
            for (Map.Entry<String, List<CustomerFact>> entry : byHash.entrySet()) {
                if (entry.getValue().size() < 2) {
                    continue;
                }
                List<CustomerFact> group = entry.getValue().stream()
                        .sorted(Comparator.comparing(c -> c.id().toString()))
                        .toList();
                for (int i = 1; i < group.size(); i++) {
                    findings.add(finding(params, QualityRuleCode.DUPLICATE_DOCUMENT,
                            null, group.get(i).id(),
                            "mesmo indice cego de documento em " + group.size()
                                    + " clientes ativos (hash " + entry.getKey().substring(0,
                                            Math.min(8, entry.getKey().length())) + "...)"));
                }
            }
        } else {
            skipped.add(new SkippedRule(QualityRuleCode.DUPLICATE_DOCUMENT, SkipReason.DISABLED));
        }

        if (params.ruleEnabled(QualityRuleCode.DUPLICATE_INSCRIPTION)) {
            // So unidades ATIVAS: a inscricao duplicada e sobre "unidades
            // ativas" (correcao D5 — PROVISIONAL em formacao nao conta nem
            // como par nem como excedente, como nas regras irmas).
            Map<String, List<UnitFact>> byInscription = snapshot.units().stream()
                    .filter(u -> !u.deleted() && u.active()
                            && u.blockId() != null && u.lot() != null && u.sublot() != null)
                    .collect(Collectors.groupingBy(u -> u.blockId() + "/" + u.lot() + "/" + u.sublot()));
            for (List<UnitFact> groupRaw : byInscription.values()) {
                if (groupRaw.size() < 2) {
                    continue;
                }
                List<UnitFact> group = groupRaw.stream()
                        .sorted(Comparator.comparing(u -> u.id().toString()))
                        .toList();
                UnitFact first = group.get(0);
                for (int i = 1; i < group.size(); i++) {
                    findings.add(finding(params, QualityRuleCode.DUPLICATE_INSCRIPTION,
                            group.get(i).id(), null,
                            "mesma inscricao (quadra/lote/sublote " + first.lot() + "/" + first.sublot()
                                    + ") em " + group.size() + " unidades ativas"));
                }
            }
        } else {
            skipped.add(new SkippedRule(QualityRuleCode.DUPLICATE_INSCRIPTION, SkipReason.DISABLED));
        }
    }

    // ------------------------------------------------------------------
    // Unidade ativa sem usuario vigente
    // ------------------------------------------------------------------

    private void evaluateUnitWithoutUser(CadastralSnapshot snapshot, QualityParameters params,
                                         List<QualityFinding> findings, List<SkippedRule> skipped) {
        if (!params.ruleEnabled(QualityRuleCode.UNIT_WITHOUT_USER)) {
            skipped.add(new SkippedRule(QualityRuleCode.UNIT_WITHOUT_USER, SkipReason.DISABLED));
            return;
        }
        Set<UUID> unitsWithUser = snapshot.links().stream()
                .filter(l -> l.current() && "USER".equals(l.role()))
                .map(LinkFact::unitId)
                .collect(Collectors.toSet());
        for (UnitFact unit : snapshot.units()) {
            if (!unit.deleted() && unit.active() && !unitsWithUser.contains(unit.id())) {
                findings.add(finding(params, QualityRuleCode.UNIT_WITHOUT_USER, unit.id(), null,
                        "unidade ativa sem vinculo vigente de usuario (papel USER em unit_customers)"));
            }
        }
    }

    // ------------------------------------------------------------------
    // Conta nao entregue (P2.8, JOR-35)
    // ------------------------------------------------------------------

    private void evaluateUndelivered(CadastralSnapshot snapshot, QualityParameters params,
                                     LocalDate referenceDate, List<QualityFinding> findings,
                                     List<SkippedRule> skipped) {
        if (!params.ruleEnabled(QualityRuleCode.BILL_UNDELIVERED)) {
            skipped.add(new SkippedRule(QualityRuleCode.BILL_UNDELIVERED, SkipReason.DISABLED));
            return;
        }
        OptionalInt window = params.undeliveredWindowDays();
        if (window.isEmpty()) {
            skipped.add(new SkippedRule(QualityRuleCode.BILL_UNDELIVERED, SkipReason.UNCONFIGURED));
            return;
        }
        LocalDate from = referenceDate.minusDays(window.getAsInt());
        Map<UUID, List<UndeliveredFact>> byUnit = snapshot.undelivered().stream()
                .filter(f -> f.returnDate() != null
                        && !f.returnDate().isAfter(referenceDate)
                        && !f.returnDate().isBefore(from))
                .collect(Collectors.groupingBy(UndeliveredFact::unitId));
        Set<UUID> deletedUnits = snapshot.units().stream()
                .filter(UnitFact::deleted)
                .map(UnitFact::id)
                .collect(Collectors.toSet());
        for (Map.Entry<UUID, List<UndeliveredFact>> entry : byUnit.entrySet()) {
            if (deletedUnits.contains(entry.getKey())) {
                continue;
            }
            List<UndeliveredFact> returns = entry.getValue().stream()
                    .sorted(Comparator.comparing(UndeliveredFact::returnDate))
                    .toList();
            UndeliveredFact latest = returns.get(returns.size() - 1);
            boolean unverified = returns.stream()
                    .anyMatch(f -> REASON_ADDRESS_NOT_FOUND.equals(f.reasonKey()));
            findings.add(finding(params, QualityRuleCode.BILL_UNDELIVERED, entry.getKey(), null,
                    returns.size() + " retorno(s) de nao entrega na janela de " + window.getAsInt()
                            + " dias; ultimo: motivo " + latest.reasonKey() + ", origem "
                            + latest.originKey() + ", data " + latest.returnDate()
                            + (unverified ? "; endereco nao verificado (P2.8)" : "")));
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private QualityFinding finding(QualityParameters params, QualityRuleCode code,
                                   UUID unitId, UUID customerId, String detail) {
        return new QualityFinding(code, params.severityFor(code), unitId, customerId, detail);
    }

    /** Classe regulatoria principal: a de mais economias vigentes; desempate alfabetico. */
    static String principalRegulatoryClass(CadastralSnapshot snapshot, UUID unitId) {
        Map<String, Integer> byClass = new LinkedHashMap<>();
        for (EconomyFact economy : snapshot.economies()) {
            if (economy.current() && economy.unitId().equals(unitId)
                    && economy.regulatoryClass() != null) {
                byClass.merge(economy.regulatoryClass(), economy.economies(), Integer::sum);
            }
        }
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : byClass.entrySet()) {
            if (entry.getValue() > bestCount
                    || (entry.getValue() == bestCount && best != null
                            && entry.getKey().compareTo(best) < 0)) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    static int totalEconomies(CadastralSnapshot snapshot, UUID unitId) {
        int total = 0;
        for (EconomyFact economy : snapshot.economies()) {
            if (economy.current() && economy.unitId().equals(unitId)) {
                total += economy.economies();
            }
        }
        return total;
    }
}
