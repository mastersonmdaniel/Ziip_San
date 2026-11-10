package br.com.ziipsan.platform.portal.selfcare;

import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.HolidayScope;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * MM-08 — Contexto municipal do portal do consumidor (ED-38), por UC.
 *
 * <p>O portal continua UNICO por empresa: o que muda e o CONTEUDO
 * contextual resolvido para a UC apresentada — nunca nasce um segundo
 * portal. As pecas do contexto sao todas existentes, consumidas por
 * nome (nada e recriado aqui):
 *
 * <ol>
 *   <li><b>Municipio da UC</b> — derivacao canonica do MM-01
 *       ({@code MunicipalityDerivation} / view
 *       {@code consumer_unit_municipality}: cadeia
 *       {@code consumer_units -> blocks -> commercial_sectors ->
 *       localities.municipality_ibge}), exposta a este pacote pela
 *       porta {@link UnitMunicipalityPort}; o adaptador de integracao
 *       e essa derivacao, sem segunda cadeia;</li>
 *   <li><b>Calendario aplicavel</b> — linhas da {@code holidays}
 *       vigente (collection V6_1 / {@code cal.holidays}: esferas
 *       NATIONAL/STATE/MUNICIPAL, MUNICIPAL com
 *       {@code municipality_ibge}) filtradas pelo mecanismo vigente
 *       {@link DeadlineCalculator#filterHolidayDates} sob o
 *       {@code HolidayScope} do tenant ({@code sac.holiday_scope}):
 *       feriado municipal so entra se o escopo do tenant o admitir
 *       (NATIONAL_STATE_MUNICIPAL) e o IBGE for o da UC;</li>
 *   <li><b>Parametros do selfcare</b> — {@link MunicipalSelfCareParameters}
 *       (regra do MM-02 sobre as chaves que a ED-38 ja tem).</li>
 * </ol>
 *
 * <p><b>Perfil regulatorio (P2.31) — FORA, com nota:</b> nenhuma classe
 * da ED-38 expoe agencia/ato regulatorio ao consumidor; este pacote
 * nao inventa essa exposicao (pendencia P-MM08-02 do RELATORIO).
 *
 * <p><b>Fallback integro:</b> UC sem municipio derivavel (a derivacao
 * do MM-01 devolve vazio: sem quadra, cadeia quebrada ou IBGE nulo)
 * produz o contexto da empresa — os parametros devolvidos sao a
 * PROPRIA porta vigente da ED-38 (sem decorador) e o calendario e o
 * que o mecanismo vigente resolve sem municipio (so nacional/estadual
 * conforme o escopo): exactamente o comportamento de hoje.
 */
public final class PortalMunicipalContext {

    /**
     * Porta do municipio da UC. Contrato do adaptador (integracao):
     * delegar a derivacao do MM-01 — {@code MunicipalityDerivation.derive}
     * sobre a cadeia territorial carregada, ou a view
     * {@code consumer_unit_municipality} — devolvendo vazio exactamente
     * nos casos em que ela devolve vazio.
     */
    public interface UnitMunicipalityPort {
        Optional<String> municipalityIbgeOf(UUID tenantId, UUID unitId);
    }

    /**
     * Porta do registo de feriados vigente ({@code holidays}): devolve
     * as linhas do tenant no formato que o mecanismo vigente ja consome
     * ({@link DeadlineCalculator.HolidayEntry}).
     */
    public interface HolidayRegisterPort {
        Collection<DeadlineCalculator.HolidayEntry> holidays(UUID tenantId);
    }

    /**
     * Contexto resolvido de uma UC.
     *
     * @param municipalityIbge municipio derivado da UC; vazio = fallback-empresa
     * @param parameters       parametros efectivos do selfcare para esta UC
     *                         (a propria porta da empresa no fallback)
     * @param holidayDates     datas de feriado aplicaveis a esta UC pelo
     *                         mecanismo vigente (nacional + estadual da UF
     *                         + municipal do IBGE, conforme o escopo)
     */
    public record Context(Optional<String> municipalityIbge, SelfCareParameters parameters,
                          Set<LocalDate> holidayDates) {
        public Context {
            if (municipalityIbge == null || parameters == null || holidayDates == null) {
                throw new IllegalArgumentException("contexto do portal incompleto");
            }
            holidayDates = Set.copyOf(holidayDates);
        }

        public boolean municipal() {
            return municipalityIbge.isPresent();
        }

        public boolean isHoliday(LocalDate date) {
            return holidayDates.contains(date);
        }
    }

    private final UnitMunicipalityPort unitMunicipality;
    private final HolidayRegisterPort holidayRegister;
    private final SelfCareParameters companyParameters;
    private final MunicipalSelfCareParameters.ScopedVersionsPort scopedVersions;

    public PortalMunicipalContext(UnitMunicipalityPort unitMunicipality,
                                  HolidayRegisterPort holidayRegister,
                                  SelfCareParameters companyParameters,
                                  MunicipalSelfCareParameters.ScopedVersionsPort scopedVersions) {
        if (unitMunicipality == null || holidayRegister == null
                || companyParameters == null || scopedVersions == null) {
            throw new IllegalArgumentException("portas do contexto municipal sao obrigatorias");
        }
        this.unitMunicipality = unitMunicipality;
        this.holidayRegister = holidayRegister;
        this.companyParameters = companyParameters;
        this.scopedVersions = scopedVersions;
    }

    /**
     * Resolve o contexto da UC.
     *
     * @param holidayScope escopo de feriados vigente do tenant
     *                     ({@code sac.holiday_scope}, P0.19) — entrada do
     *                     chamador, como no mecanismo vigente; nao e
     *                     presumido aqui
     * @param stateCode    UF da localidade da UC, para as linhas STATE
     *                     do mecanismo vigente (pode ser null: linhas
     *                     estaduais simplesmente nao casam, como hoje)
     * @param at           instante da resolucao dos parametros (MM-02)
     */
    public Context resolve(UUID tenantId, UUID unitId, HolidayScope holidayScope,
                           String stateCode, LocalDate at) {
        if (tenantId == null || unitId == null || holidayScope == null || at == null) {
            throw new IllegalArgumentException("tenant, unidade, escopo de feriados e instante sao obrigatorios");
        }
        Optional<String> ibge = unitMunicipality.municipalityIbgeOf(tenantId, unitId);
        if (ibge == null) {
            throw new IllegalStateException("porta de municipio da UC devolveu null");
        }
        Collection<DeadlineCalculator.HolidayEntry> entries = holidayRegister.holidays(tenantId);
        if (ibge.isEmpty()) {
            // Fallback integro: parametros = a propria porta vigente;
            // calendario = o mecanismo vigente sem municipio.
            Set<LocalDate> dates = DeadlineCalculator.filterHolidayDates(
                    entries, holidayScope, stateCode, null);
            return new Context(Optional.empty(), companyParameters, dates);
        }
        SelfCareParameters effective = new MunicipalSelfCareParameters(
                companyParameters, scopedVersions, ibge.get(), at);
        Set<LocalDate> dates = DeadlineCalculator.filterHolidayDates(
                entries, holidayScope, stateCode, ibge.get());
        return new Context(ibge, effective, dates);
    }
}
