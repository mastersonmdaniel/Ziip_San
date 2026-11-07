package br.com.ziipsan.platform.central.complete;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * P3.15 — aba Regulatório: leitura da biblioteca de normas da P1.34.
 *
 * <p>A fonte é o catálogo global do core (tabelas da core {@code V9_8},
 * P1.32, carregadas pela {@code R__norm_catalog.sql} da P1.34) e a
 * consulta pública {@code GET /api/v1/certification/standards}, que
 * aceita {@code standardClass}, {@code area}, {@code module} (token
 * inteiro), {@code status} (só os 4 valores do CHECK) e {@code agency}.
 * Esta classe é a projeção de LEITURA dessa biblioteca para a Central:
 * situação, versões (edições com vigência), vigência futura e perfil
 * por agência. Nenhum campo carrega texto de norma — por construção:
 * {@link NormEntry} só tem metadados e o resumo em palavras próprias da
 * carga, como na P1.34.
 *
 * <h2>Projeção fiel da V9_8/P1.34 (correção pós-revisão, achado B2)</h2>
 * <ul>
 *   <li>{@code effective_from}/{@code effective_to} são <b>ANULÁVEIS</b>
 *       nas DUAS tabelas da V9_8 ({@code cert_standard} e
 *       {@code cert_standard_edition}); a carga da P1.34 cura a data de
 *       efeito no nível da <b>NORMA</b> ({@code cert_standard.effective_from})
 *       e cria <b>uma edição por norma</b>. Dos 14 itens em vigência
 *       futura da carga, 1 ficou com {@code effective_from} NULL
 *       ({@code nt-2026-001-rtc-nfag}, sem data fixada na fonte). Logo
 *       {@link NormEntry} carrega a data DA NORMA (anulável, aceita sem
 *       erro) e {@link NormEdition} também aceita datas anuláveis —
 *       recusar {@code null} quebraria a projeção do catálogo real.</li>
 *   <li>A regra de transição é a ÚNICA do core
 *       ({@code NormEffectivenessService}, P1.34):
 *       {@code status = 'VIGENCIA_FUTURA' AND effective_from IS NOT NULL
 *       AND effective_from <= hoje → VIGENTE}, sobre a data <b>da
 *       norma</b>, <b>sem</b> condição de {@code effective_to}. Norma
 *       sem data permanece {@code VIGENCIA_FUTURA}, sem erro e sem
 *       transição, até uma versão futura do catálogo trazer a data.
 *       {@link #statusAt(NormEntry, LocalDate)} reproduz exatamente
 *       essa regra — a versão anterior olhava as EDIÇÕES e exigia
 *       {@code effective_to} vigente, divergindo da P1.34.</li>
 * </ul>
 *
 * <h2>Obrigatórias não desativáveis (correção pós-revisão, achado M1)</h2>
 * Não existe coluna {@code mandatory} na V9_8 nem na P1.34: a
 * não-desativação <b>deriva da CLASSE</b> — {@code CertificationService.deactivate}
 * (core) recusa norma {@code REGULATORY} e o CHECK
 * {@code chk_cert_activation_regulatory_never_inactive} da tenant
 * {@code V21_1} proíbe {@code REGULATORY + INACTIVE} no banco. Por isso
 * {@link NormEntry#mandatory()} é método derivado
 * ({@code standardClass == REGULATORY}), não campo solto — a versão
 * anterior tinha um {@code boolean mandatory} livre que permitia a
 * contradição "REGULATORY não obrigatória" nas fixtures.
 */
public final class RegulatoryLibrary {

    /** Situações do CHECK da V9_8 (mapeamento da carga da P1.34). */
    public static final Set<String> STATUSES = Set.of("VIGENTE", "VIGENCIA_FUTURA", "REVOGADA", "SUBSTITUIDA");
    /** Classes da P1.32/P1.34. */
    public static final Set<String> CLASSES = Set.of("REGULATORY", "VOLUNTARY", "REFERENCE", "EXPORT_PROFILE");
    /** Selos de verificação da carga da P1.34. */
    public static final Set<String> SEALS = Set.of("OFICIAL", "SECUNDARIA", "NAO_CONFIRMADA");

    /**
     * Uma edição (versão) da norma. Na V9_8 as datas da edição também
     * são anuláveis; a carga da P1.34 cria uma edição por norma e a data
     * que manda na transição é a DA NORMA (ver {@link NormEntry}).
     * {@code effectiveTo} só é validado contra {@code effectiveFrom}
     * quando AMBAS existem (o CHECK da V9_8 é exatamente esse).
     */
    public record NormEdition(String edition, LocalDate effectiveFrom, LocalDate effectiveTo) {
        public NormEdition {
            if (edition == null || edition.isBlank()) {
                throw new IllegalArgumentException("Edição da norma é obrigatória");
            }
            if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
                throw new IllegalArgumentException("effective_to anterior a effective_from na edição " + edition);
            }
        }

        /** Vigência futura em relação à referência: edição COM data ainda não chegada. Sem data, não há futuro a antecipar. */
        public boolean isFutureAt(LocalDate reference) {
            Objects.requireNonNull(reference, "referência é obrigatória");
            return effectiveFrom != null && effectiveFrom.isAfter(reference);
        }
    }

    /**
     * Entrada da biblioteca (metadados da P1.34 — nunca texto de norma).
     *
     * @param effectiveFrom data de efeito DA NORMA ({@code cert_standard.effective_from}),
     *                      ANULÁVEL na V9_8: norma em vigência futura sem
     *                      data permanece {@code VIGENCIA_FUTURA} (P1.34)
     * @param effectiveTo   fim de vigência DA NORMA, ANULÁVEL; NÃO entra
     *                      na regra de transição da P1.34
     */
    public record NormEntry(String catalogKey, String title, String standardClass, String status,
                            String verificationSeal, String area, String agency, String module,
                            String officialSourceUrl, String scopeSummary,
                            LocalDate effectiveFrom, LocalDate effectiveTo,
                            List<NormEdition> editions) {
        public NormEntry {
            if (catalogKey == null || catalogKey.isBlank()) {
                throw new IllegalArgumentException("catalog_key (id estável da P1.34) é obrigatório");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Título da norma " + catalogKey + " é obrigatório");
            }
            if (!CLASSES.contains(standardClass)) {
                throw new IllegalArgumentException("Classe inválida para " + catalogKey + ": " + standardClass);
            }
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("Situação fora dos 4 valores da V9_8 para " + catalogKey + ": " + status);
            }
            if (!SEALS.contains(verificationSeal)) {
                throw new IllegalArgumentException("Selo de verificação inválido para " + catalogKey + ": " + verificationSeal);
            }
            if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
                throw new IllegalArgumentException("effective_to anterior a effective_from na norma " + catalogKey);
            }
            if (editions == null || editions.isEmpty()) {
                throw new IllegalArgumentException("Norma " + catalogKey + " sem edição: a carga da P1.34 cria ao menos uma");
            }
            editions = List.copyOf(editions);
        }

        /**
         * Obrigatória = classe {@code REGULATORY} (M1): é da classe que
         * deriva a não-desativação no core ({@code CertificationService.deactivate}
         * recusa REGULATORY; CHECK da V21_1), não de um booleano livre.
         */
        public boolean mandatory() {
            return "REGULATORY".equals(standardClass);
        }

        /** Edições com vigência futura na referência (selo de vigência futura da P1.34). */
        public List<NormEdition> futureEditions(LocalDate reference) {
            Objects.requireNonNull(reference, "referência é obrigatória");
            return editions.stream().filter(e -> e.isFutureAt(reference)).toList();
        }
    }

    /**
     * Perfil regulatório por agência.
     *
     * @param retainedMandatoryKeys normas {@code REGULATORY} cuja exclusão
     *                              foi TENTADA e RECUSADA: elas permanecem
     *                              em {@code norms} e a tentativa fica
     *                              sinalizada aqui (M1) — obrigatória não
     *                              sai do perfil, como não desativa no core
     */
    public record AgencyProfile(String agency, List<NormEntry> norms, List<NormEntry> mandatoryNorms,
                                List<String> retainedMandatoryKeys) {
    }

    /** Filtro espelhando os parâmetros da consulta da P1.34; campo nulo = sem filtro naquele eixo. */
    public record NormFilter(String standardClass, String area, String moduleToken, String status, String agency) {
        public NormFilter {
            if (standardClass != null && !CLASSES.contains(standardClass)) {
                throw new IllegalArgumentException("Classe de filtro inválida: " + standardClass);
            }
            if (status != null && !STATUSES.contains(status)) {
                throw new IllegalArgumentException("Situação de filtro fora dos 4 valores da V9_8: " + status);
            }
        }
    }

    private final List<NormEntry> catalog;

    public RegulatoryLibrary(List<NormEntry> catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catálogo de normas é obrigatório (ausência de carga é erro, não catálogo vazio presumido)");
        }
        this.catalog = List.copyOf(catalog);
    }

    /**
     * Consulta por classe, área, módulo (token inteiro, como na API da
     * P1.34), situação e agência. A situação comparada é a DERIVADA em
     * {@link #statusAt(NormEntry, LocalDate)} na {@code reference} (M5) —
     * consistente com a leitura da aba: uma norma gravada como
     * {@code VIGENCIA_FUTURA} cuja data da norma já chegou é encontrada
     * pelo filtro {@code VIGENTE}, não pelo gravado.
     */
    public List<NormEntry> query(NormFilter filter, LocalDate reference) {
        Objects.requireNonNull(filter, "filtro é obrigatório");
        Objects.requireNonNull(reference, "referência é obrigatória (a situação do filtro é a derivada nela)");
        return catalog.stream()
                .filter(n -> filter.standardClass() == null || n.standardClass().equals(filter.standardClass()))
                .filter(n -> filter.area() == null || filter.area().equals(n.area()))
                .filter(n -> filter.status() == null || statusAt(n, reference).equals(filter.status()))
                .filter(n -> filter.agency() == null || filter.agency().equals(n.agency()))
                .filter(n -> filter.moduleToken() == null || hasModuleToken(n, filter.moduleToken()))
                .toList();
    }

    private static boolean hasModuleToken(NormEntry norm, String token) {
        if (norm.module() == null) {
            return false;
        }
        for (String part : norm.module().split(",")) {
            if (part.trim().equals(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Situação NA data, pela regra ÚNICA da P1.34
     * ({@code NormEffectivenessService}): norma gravada
     * {@code VIGENCIA_FUTURA} com {@code effective_from} DA NORMA não
     * nulo e {@code <= data} lê-se {@code VIGENTE}; sem data, permanece
     * {@code VIGENCIA_FUTURA} (sem erro); as demais situações gravadas
     * não mudam. {@code effective_to} NÃO participa da regra.
     */
    public static String statusAt(NormEntry norm, LocalDate date) {
        Objects.requireNonNull(norm, "norma é obrigatória");
        Objects.requireNonNull(date, "data é obrigatória");
        if (!"VIGENCIA_FUTURA".equals(norm.status())) {
            return norm.status();
        }
        if (norm.effectiveFrom() != null && !norm.effectiveFrom().isAfter(date)) {
            return "VIGENTE";
        }
        return "VIGENCIA_FUTURA";
    }

    /** Normas com alguma edição de vigência futura na referência (alerta antecipado da P1.34/ED-98). */
    public List<NormEntry> withFutureEffect(LocalDate reference) {
        Objects.requireNonNull(reference, "referência é obrigatória");
        return catalog.stream().filter(n -> !n.futureEditions(reference).isEmpty()).toList();
    }

    /** Perfil por agência sem exclusões pedidas: todas as normas da agência. */
    public AgencyProfile profileForAgency(String agency) {
        return profileForAgency(agency, List.of());
    }

    /**
     * Perfil por agência com tentativa de exclusão (M1): excluir norma
     * que NÃO é {@code REGULATORY} funciona; tentar excluir/desativar
     * norma {@code REGULATORY} é RECUSADO — ela permanece no perfil e a
     * tentativa é sinalizada em {@code retainedMandatoryKeys}, espelhando
     * a recusa do {@code CertificationService.deactivate} e o CHECK da
     * V21_1 no core. Chave desconhecida no pedido de exclusão é erro
     * explícito (não é ignorada em silêncio).
     */
    public AgencyProfile profileForAgency(String agency, List<String> excludedCatalogKeys) {
        if (agency == null || agency.isBlank()) {
            throw new IllegalArgumentException("Agência é obrigatória para montar o perfil regulatório");
        }
        Objects.requireNonNull(excludedCatalogKeys, "lista de exclusões é obrigatória (vazia = sem exclusão)");
        List<NormEntry> agencyNorms = catalog.stream().filter(n -> agency.equals(n.agency())).toList();
        List<String> retained = new ArrayList<>();
        List<NormEntry> norms = new ArrayList<>();
        for (NormEntry norm : agencyNorms) {
            if (!excludedCatalogKeys.contains(norm.catalogKey())) {
                norms.add(norm);
            } else if (norm.mandatory()) {
                // REGULATORY não sai: permanece e a tentativa é sinalizada.
                norms.add(norm);
                retained.add(norm.catalogKey());
            }
            // Não obrigatória excluída: sai do perfil, sem sinalização.
        }
        for (String key : excludedCatalogKeys) {
            boolean inProfile = agencyNorms.stream().anyMatch(n -> n.catalogKey().equals(key));
            if (!inProfile) {
                throw new IllegalArgumentException(
                        "Exclusão pede norma fora do perfil da agência " + agency + ": " + key);
            }
        }
        List<NormEntry> kept = List.copyOf(norms);
        return new AgencyProfile(agency, kept,
                kept.stream().filter(NormEntry::mandatory).toList(), List.copyOf(retained));
    }
}
