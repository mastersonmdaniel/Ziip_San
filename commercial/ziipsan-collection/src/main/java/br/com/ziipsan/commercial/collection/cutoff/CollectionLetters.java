package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cartas de cobranca (TR-390/SG-375, 18e-E9): selecao pelos filtros do
 * edital — imovel (unidade), rotas, referencia, valor, quantidade de
 * faturas e meses de atraso — e emissao pelo motor de modelos da P2.30
 * (JOR-06), com o codigo de modelo do tenant
 * ({@code cut.collection_letter_template}). A carta emitida grava
 * codigo e versao do modelo (regra da P2.30); o envio com prova de
 * entrega e da P3.5, nao desta classe.
 */
public final class CollectionLetters {

    private CollectionLetters() {
    }

    /** Fatos da unidade para a selecao da carta (cadastro P1.3 + faturamento P2.5). */
    public record LetterCandidate(UUID unitId, String routeId, YearMonth reference,
                                  BigDecimal overdueTotal, int overdueBillCount, int monthsLate,
                                  Map<String, Object> letterVariables) {
        public LetterCandidate {
            if (unitId == null || routeId == null || reference == null || overdueTotal == null) {
                throw new IllegalArgumentException(
                        "candidata a carta exige unidade, rota, referencia e valor vencido");
            }
            if (overdueBillCount < 0 || monthsLate < 0) {
                throw new IllegalArgumentException("quantidade de faturas e meses de atraso nao podem ser negativos");
            }
            letterVariables = letterVariables == null ? Map.of() : Map.copyOf(letterVariables);
        }
    }

    /** Filtros de TR-390/SG-375; campo ausente (null) nao filtra por ele. */
    public record LetterFilters(UUID unitId, List<String> routeIds, YearMonth reference,
                                BigDecimal minAmount, BigDecimal maxAmount,
                                Integer minBillCount, Integer minMonthsLate) {
        public LetterFilters {
            routeIds = routeIds == null ? List.of() : List.copyOf(routeIds);
            if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
                throw new IllegalArgumentException("faixa de valor invalida nos filtros da carta");
            }
        }

        public boolean matches(LetterCandidate candidate) {
            if (unitId != null && !unitId.equals(candidate.unitId())) {
                return false;
            }
            if (!routeIds.isEmpty() && !routeIds.contains(candidate.routeId())) {
                return false;
            }
            if (reference != null && !reference.equals(candidate.reference())) {
                return false;
            }
            if (minAmount != null && candidate.overdueTotal().compareTo(minAmount) < 0) {
                return false;
            }
            if (maxAmount != null && candidate.overdueTotal().compareTo(maxAmount) > 0) {
                return false;
            }
            if (minBillCount != null && candidate.overdueBillCount() < minBillCount) {
                return false;
            }
            return minMonthsLate == null || candidate.monthsLate() >= minMonthsLate;
        }
    }

    /** Carta emitida: unidade, modelo/versao aplicados e referencia do documento gerado. */
    public record IssuedLetter(UUID unitId, String templateCode, String templateVersion, String documentRef) {
    }

    /** Selecao pura pelos filtros (sem emissao). */
    public static List<LetterCandidate> select(List<LetterCandidate> candidates, LetterFilters filters) {
        if (candidates == null || filters == null) {
            throw new IllegalArgumentException("candidatas e filtros sao obrigatorios");
        }
        return candidates.stream().filter(filters::matches).toList();
    }

    /** Emite uma carta por unidade selecionada, pelo motor de modelos da P2.30. */
    public static List<IssuedLetter> issue(UUID tenantId, List<LetterCandidate> candidates,
                                           LetterFilters filters, DunningPorts.TemplatePort templates,
                                           CutoffExtParameters extParams) {
        if (tenantId == null || templates == null || extParams == null) {
            throw new IllegalArgumentException("tenant, motor de modelos e parametros sao obrigatorios");
        }
        String templateCode = extParams.collectionLetterTemplate();
        List<IssuedLetter> issued = new ArrayList<>();
        for (LetterCandidate candidate : select(candidates, filters)) {
            var rendered = templates.render(tenantId, templateCode, candidate.letterVariables());
            issued.add(new IssuedLetter(candidate.unitId(), rendered.templateCode(),
                    rendered.templateVersion(), rendered.documentRef()));
        }
        return List.copyOf(issued);
    }
}
