package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.api.ApiException;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * The execution gate of the cutoff (P3.4), applied when the cutoff work
 * order is created and reused conceptually by the revalidation at
 * execution. Pure; refusals are 409 conflicts naming the rule, in the
 * platform style ("O bloqueio impede a OS, não só avisa" — G9).
 *
 * <p>Order of checks:
 * <ol>
 *   <li><b>Emergency evidence:</b> a technical-emergency cutoff requires
 *       justification AND photo (also a CHECK in V9_2). This is checked
 *       for every emergency, judicial order or not.</li>
 *   <li><b>Protected classes, for ALL reasons:</b> an essential user or a
 *       health-dependent person (registry of P1.3, CAD-80) is never cut
 *       by this flow; the social-tariff class is cut only when the
 *       agency parameter allows it and only for the debt reason.</li>
 *   <li><b>Sewer:</b> cutting the sewer connection requires
 *       {@code cut.sewer_allowed} (factory default: no).</li>
 *   <li><b>Temporal bans:</b> reasons follow the bans per
 *       {@code cut.reasons} (factory default: all follow, except
 *       technical emergency WITH justification and photo). A judicial
 *       order is the only other exception, and it lifts ONLY the
 *       temporal bans — never the protected-class or evidence rules.
 *       The debt reason additionally requires the notice gate of
 *       {@link CutoffNoticeRule} (proof, period elapsed, not expired).</li>
 * </ol>
 */
public final class CutoffExecutionRule {

    private CutoffExecutionRule() {
    }

    /** Everything the gate needs; the notice is required only for the debt reason. */
    public record GateInput(
            CutoffEnums.Reason reason,
            boolean sewer,
            ZonedDateTime when,
            CutoffCalendar.CalendarData calendar,
            boolean judicialOrder,
            String emergencyJustification,
            UUID emergencyPhotoDocumentId,
            Set<CutoffEnums.ProtectedClass> unitProtectedClasses,
            CutoffNoticeRule.Notice notice,
            CutoffParameters.Values params) {
        public GateInput {
            if (reason == null || when == null || calendar == null || params == null) {
                throw new IllegalArgumentException("motivo, instante, calendario e parametros sao obrigatorios");
            }
            unitProtectedClasses = unitProtectedClasses == null ? Set.of() : Set.copyOf(unitProtectedClasses);
        }
    }

    /** Throws {@link ApiException.Conflict} naming the first violated rule. */
    public static void assertAllowed(GateInput input) {
        // 1. Emergency evidence.
        if (input.reason() == CutoffEnums.Reason.TECHNICAL_EMERGENCY) {
            boolean hasJustification = input.emergencyJustification() != null
                    && !input.emergencyJustification().isBlank();
            if (!hasJustification || input.emergencyPhotoDocumentId() == null) {
                throw new ApiException.Conflict("cutoff-emergency-evidence-required",
                        "Corte de emergencia tecnica exige justificativa E foto anexadas a OS.");
            }
        }
        // 2. Protected classes, for all reasons.
        if (input.unitProtectedClasses().contains(CutoffEnums.ProtectedClass.ESSENTIAL_USER)
                || input.unitProtectedClasses().contains(CutoffEnums.ProtectedClass.HEALTH_DEPENDENT)) {
            throw new ApiException.Conflict("cutoff-protected-class",
                    "Unidade de usuario essencial ou de pessoa dependente de abastecimento continuo:"
                            + " corte vedado para todos os motivos (cadastro da P1.3, CAD-80).");
        }
        if (input.reason() == CutoffEnums.Reason.DEFAULT
                && input.unitProtectedClasses().contains(CutoffEnums.ProtectedClass.SOCIAL_TARIFF)
                && input.params().protectedClasses().contains(CutoffEnums.ProtectedClass.SOCIAL_TARIFF)
                && !input.params().socialTariffCutAllowed()) {
            throw new ApiException.Conflict("cutoff-social-tariff-protected",
                    "Beneficiario da tarifa social: corte so se a ERI previr (cut.social_tariff_allowed).");
        }
        // 3. Sewer.
        if (input.sewer() && !input.params().sewerAllowed()) {
            throw new ApiException.Conflict("cutoff-sewer-not-allowed",
                    "Interrupcao de esgoto nao permitida (cut.sewer_allowed; Lei 11.445/2007, art. 40, V).");
        }
        // 4. Temporal bans (judicial order lifts only this layer).
        boolean emergencyExempt = input.reason() == CutoffEnums.Reason.TECHNICAL_EMERGENCY;
        boolean followsBans = input.params().reasonFollowsTemporalBans(input.reason())
                && !emergencyExempt;
        if (followsBans && !input.judicialOrder()
                && CutoffCalendar.isForbidden(input.when(), input.calendar())) {
            throw new ApiException.Conflict("cutoff-temporal-ban",
                    "Vedacao temporal de corte (Lei 14.015/2020 e, em SC, Lei SC 11.959/2001 com a"
                            + " redacao da Lei SC 17.780/2019) no instante pedido.");
        }
        // 5. Notice gate of the debt reason.
        if (input.reason() == CutoffEnums.Reason.DEFAULT) {
            if (input.notice() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "noticeId", "REQUIRED", "Corte por inadimplencia exige o aviso de corte."));
            }
            var failure = CutoffNoticeRule.noticeFailure(
                    input.notice(), input.when().toLocalDate(), input.params());
            if (failure.isPresent()) {
                throw new ApiException.Conflict("cutoff-notice-" + failure.get().name().toLowerCase(),
                        "Aviso de corte nao sustenta o corte: " + failure.get() + ".");
            }
        }
    }
}
