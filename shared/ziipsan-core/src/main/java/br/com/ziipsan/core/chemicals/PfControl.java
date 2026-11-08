package br.com.ziipsan.core.chemicals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Controle OPCIONAL, por tenant, dos produtos sujeitos a Policia
 * Federal (ED-89, OPE-06; Lei 10.357/2001; Portaria MJSP 204/2022,
 * alterada pela Portaria MJSP 223/2022).
 *
 * <p>Portoes duros do acceptance, nesta ordem:
 * <ol>
 *   <li>ANTES de ligar o controle, os insumos do tenant sao conferidos
 *       contra a lista VIGENTE ({@link PreEnableCheck}) — ligar sem
 *       conferencia registrada e recusado;</li>
 *   <li>todo produto casado com a lista exige CADASTRO no controle
 *       ({@link PfRegistration}, referencia declarada pelo tenant —
 *       o formato do registro na PF nao consta das fontes [SEM FONTE]
 *       e nao e inventado aqui);</li>
 *   <li>ligar exige LICENCA DE FUNCIONAMENTO vigente
 *       ({@link PfLicence}); licenca vencida bloqueia o recebimento de
 *       produto controlado e marca o controle como nao conforme;</li>
 *   <li>INFORMACOES PERIODICAS exigidas pela norma vigente entram como
 *       obrigacao com prazo DECLARADO ({@link PeriodicReport}) —
 *       nenhuma periodicidade e presumida [SEM FONTE]; vencida sem
 *       envio, gera alerta.</li>
 * </ol>
 */
public final class PfControl {
    private PfControl() {}

    /** Conferencia pre-ativacao: insumos do tenant x lista vigente. */
    public record PreEnableCheck(UUID tenantId, String listVersion, LocalDate checkedOn,
                                 List<String> matchedItemCodes, Instant at) {
        public PreEnableCheck {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (listVersion == null || listVersion.isBlank()) throw new IllegalArgumentException("versao da lista da conferencia e obrigatoria");
            Objects.requireNonNull(checkedOn, "data da conferencia e obrigatoria");
            matchedItemCodes = matchedItemCodes == null ? List.of() : List.copyOf(matchedItemCodes);
        }
    }

    /** Cadastro do produto controlado junto ao controle da PF (referencia do tenant). */
    public record PfRegistration(UUID tenantId, String itemCode, String registrationRef, LocalDate registeredOn) {
        public PfRegistration {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item do cadastro PF e obrigatorio");
            if (registrationRef == null || registrationRef.isBlank())
                throw new ChemExceptions.ValidationException("PF_REGISTRATION_REF_REQUIRED",
                        "cadastro do produto controlado exige a referencia do registro: " + itemCode + " [SEM FONTE: formato nao consta das fontes]");
            Objects.requireNonNull(registeredOn, "data do cadastro PF e obrigatoria");
        }
    }

    /** Licenca de funcionamento com renovacao: cada renovacao e uma nova licenca registrada. */
    public record PfLicence(UUID tenantId, String licenceRef, LocalDate issuedOn, LocalDate expiresOn) {
        public PfLicence {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (licenceRef == null || licenceRef.isBlank()) throw new IllegalArgumentException("referencia da licenca PF e obrigatoria");
            Objects.requireNonNull(issuedOn, "emissao da licenca e obrigatoria");
            Objects.requireNonNull(expiresOn, "validade da licenca e obrigatoria");
            if (!expiresOn.isAfter(issuedOn)) throw new IllegalArgumentException("licenca PF com validade nao posterior a emissao: " + licenceRef);
        }
        public boolean validOn(LocalDate day) { return !day.isBefore(issuedOn) && !day.isAfter(expiresOn); }
    }

    /**
     * Estado do controle. {@code enabledMatchedItemCodes} e o SNAPSHOT
     * dos itens casados na conferencia que HABILITOU o controle
     * (correcao R2 da revisao da ED-89): o portao casa contra ESTA
     * conferencia/versao, nunca contra a conferencia mais recente —
     * uma conferencia nova nao altera o portao ate um novo enable.
     */
    public record PfState(UUID tenantId, boolean enabled, String enabledListVersion, Instant enabledAt,
                          List<String> enabledMatchedItemCodes) {
        public PfState {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            enabledMatchedItemCodes = enabledMatchedItemCodes == null ? List.of() : List.copyOf(enabledMatchedItemCodes);
        }
        /** Compatibilidade com estados anteriores ao snapshot (R2): sem snapshot, isControlled cai para a conferencia da versao habilitada. */
        public PfState(UUID tenantId, boolean enabled, String enabledListVersion, Instant enabledAt) {
            this(tenantId, enabled, enabledListVersion, enabledAt, List.of());
        }
        public static PfState disabled(UUID tenantId) { return new PfState(tenantId, false, null, null); }
    }

    /**
     * Informacao periodica exigida pela norma vigente: o ROTULO do
     * periodo e o PRAZO sao declarados pelo tenant/conformidade a
     * partir da norma vigente [SEM FONTE: nenhuma periodicidade consta
     * das fontes do projeto]; o nucleo so apura envio e vencimento.
     */
    public record PeriodicReport(UUID tenantId, String periodLabel, LocalDate dueOn,
                                 LocalDate submittedOn, String submissionRef) {
        public PeriodicReport {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (periodLabel == null || periodLabel.isBlank()) throw new IllegalArgumentException("rotulo do periodo da informacao periodica e obrigatorio");
            Objects.requireNonNull(dueOn, "prazo da informacao periodica e obrigatorio (declarado da norma vigente)");
            if ((submittedOn == null) != (submissionRef == null || submissionRef.isBlank()))
                throw new IllegalArgumentException("informacao periodica enviada exige data e referencia de envio; nao enviada, nenhum dos dois");
        }
        public boolean submitted() { return submittedOn != null; }
        public boolean overdueOn(LocalDate day) { return !submitted() && day.isAfter(dueOn); }
    }

    /** Roda a conferencia pre-ativacao contra a versao vigente da lista. */
    public static PreEnableCheck runPreEnableCheck(UUID tenantId, List<ChemProduct> tenantProducts,
                                                   ControlledCatalog.ListVersion currentList, Instant at) {
        List<String> matched = ControlledCatalog.matchedProducts(currentList, tenantProducts)
                .stream().map(ChemProduct::inventoryItemCode).toList();
        return new PreEnableCheck(tenantId, currentList.version(), LocalDate.ofInstant(at, java.time.ZoneOffset.UTC), matched, at);
    }

    /**
     * Liga o controle. PRIMEIRO portao (correcao R1 da revisao da
     * ED-89): a chave proposta {@code chem.pf_control_enabled} tem de
     * estar EXPLICITAMENTE ligada no tenant —
     * {@link ChemParameters#requirePfControlEnabled} recusa por nome
     * quando ausente ({@code MissingParameterException}) e o valor
     * {@code false} recusa com {@code PF_CONTROL_DISABLED}; ausencia
     * ou {@code false} NUNCA ligam por presuncao. Depois, recusa
     * nomeada quando: nao houve conferencia contra a lista vigente
     * informada; a conferencia e de outra versao; produto casado sem
     * cadastro PF; ou nao ha licenca vigente na data.
     */
    public static PfState enable(ChemStore store, ChemParameters parameters, UUID tenantId, String currentListVersion,
                                 LocalDate today, Instant at) {
        Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
        if (!ChemParameters.requirePfControlEnabled(parameters.pfControlEnabled(tenantId)))
            throw new ChemExceptions.ValidationException("PF_CONTROL_DISABLED",
                    "controle da Policia Federal desligado no tenant pela chave " + ChemParameters.PF_CONTROL_ENABLED
                            + " (chave proposta pela ED-89): ligar exige a definicao explicita como verdadeira");
        PreEnableCheck check = store.latestPfCheck(tenantId).orElseThrow(() ->
                new ChemExceptions.ValidationException("PF_CHECK_REQUIRED",
                        "ligar o controle da Policia Federal exige a conferencia previa dos insumos contra a lista vigente (OPE-06)"));
        if (!check.listVersion().equals(currentListVersion))
            throw new ChemExceptions.ValidationException("PF_CHECK_STALE",
                    "conferencia registrada e da lista " + check.listVersion() + ", nao da vigente " + currentListVersion + ": reconferir antes de ligar");
        List<PfRegistration> registrations = store.pfRegistrations(tenantId);
        for (String code : check.matchedItemCodes()) {
            if (registrations.stream().noneMatch(r -> r.itemCode().equals(code)))
                throw new ChemExceptions.ValidationException("PF_REGISTRATION_MISSING",
                        "produto controlado sem cadastro no controle da Policia Federal: " + code);
        }
        licenceValidOn(store, tenantId, today).orElseThrow(() ->
                new ChemExceptions.ValidationException("PF_LICENCE_REQUIRED",
                        "ligar o controle exige licenca de funcionamento vigente (Policia Federal)"));
        PfState state = new PfState(tenantId, true, currentListVersion, at, check.matchedItemCodes());
        store.savePfState(state);
        return state;
    }

    public static java.util.Optional<PfLicence> licenceValidOn(ChemStore store, UUID tenantId, LocalDate day) {
        return store.pfLicences(tenantId).stream().filter(l -> l.validOn(day))
                .max(java.util.Comparator.comparing(PfLicence::expiresOn));
    }

    /**
     * O item esta sob controle PF ativo neste tenant? Casa contra a
     * conferencia DA VERSAO HABILITADA (correcao R2 da revisao da
     * ED-89): primeiro o snapshot gravado no estado no enable; na
     * falta dele (estado legado), a conferencia da versao habilitada
     * ({@link ChemStore#pfCheckForVersion}). Uma conferencia MAIS
     * NOVA gravada depois do enable NAO altera este portao — nem
     * abre (fail-open) nem fecha — ate um novo {@link #enable}; essa
     * pendencia e EXPLICITA em {@link #hasPendingNewerCheck}, nunca
     * silenciosa.
     */
    public static boolean isControlled(ChemStore store, UUID tenantId, String itemCode) {
        PfState state = store.pfState(tenantId).orElse(null);
        if (state == null || !state.enabled()) return false;
        if (!state.enabledMatchedItemCodes().isEmpty())
            return state.enabledMatchedItemCodes().contains(itemCode);
        PreEnableCheck check = store.pfCheckForVersion(tenantId, state.enabledListVersion()).orElse(null);
        if (check != null) return check.matchedItemCodes().contains(itemCode);
        // Estado legado sem snapshot e sem a conferencia da versao
        // habilitada recuperavel: nenhum item consta como casado
        // naquela conferencia (o snapshot vazio de um enable real
        // tambem significa "nenhum casado") — nunca presumir a
        // conferencia mais recente, que pode ser de outra versao.
        return false;
    }

    /** Conferencia que habilitou o controle (a da versao habilitada), se recuperavel do store. */
    public static java.util.Optional<PreEnableCheck> enabledCheck(ChemStore store, UUID tenantId) {
        PfState state = store.pfState(tenantId).orElse(null);
        if (state == null || !state.enabled()) return java.util.Optional.empty();
        return store.pfCheckForVersion(tenantId, state.enabledListVersion());
    }

    /**
     * Ha conferencia mais nova do que a versao habilitada aguardando
     * um novo enable? Estado EXPLICITO (R2): enquanto {@code true},
     * o portao segue valendo pela versao habilitada
     * ({@link #isControlled}); nada muda silenciosamente.
     */
    public static boolean hasPendingNewerCheck(ChemStore store, UUID tenantId) {
        PfState state = store.pfState(tenantId).orElse(null);
        if (state == null || !state.enabled()) return false;
        PreEnableCheck latest = store.latestPfCheck(tenantId).orElse(null);
        return latest != null && !latest.listVersion().equals(state.enabledListVersion());
    }

    /**
     * Portao do recebimento: com o controle ligado, produto controlado
     * so recebe com licenca vigente na data — licenca vencida bloqueia.
     */
    public static void requireReceiptAllowed(ChemStore store, UUID tenantId, String itemCode, LocalDate today) {
        if (!isControlled(store, tenantId, itemCode)) return;
        if (licenceValidOn(store, tenantId, today).isEmpty())
            throw new ChemExceptions.ValidationException("PF_LICENCE_EXPIRED",
                    "licenca de funcionamento vencida/ausente: recebimento de produto controlado bloqueado: " + itemCode);
    }

    /** Informacoes periodicas vencidas e nao enviadas (para alerta). */
    public static List<PeriodicReport> overdueReports(ChemStore store, UUID tenantId, LocalDate today) {
        return store.periodicReports(tenantId).stream().filter(r -> r.overdueOn(today)).toList();
    }
}
