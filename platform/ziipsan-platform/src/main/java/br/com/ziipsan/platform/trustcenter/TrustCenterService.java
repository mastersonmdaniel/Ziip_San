package br.com.ziipsan.platform.trustcenter;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P3.38: montagem da visão pública do Trust Center
 * ({@code GET /api/v1/public/trust-center}), servida da mesma hospedagem
 * estática separada da produção da página de status da P3.18.
 *
 * <p>A regra que organiza tudo aqui é a do aceite lido com honestidade:
 * cada seção da página ou exibe um fato com fonte, ou se declara
 * PENDENTE — nunca preenche o vazio com conteúdo plausível. Na prática:
 * <ul>
 *   <li><b>Suboperadores (P3.9):</b> só os publicáveis
 *       ({@link Subprocessor#isPubliclyListed()}); estrangeiro sem
 *       cláusulas-padrão fica FORA da lista pública (e o cadastro
 *       continua recusando a publicação dele). Mudança anunciada aparece
 *       em seção própria, com a vigência — o aviso de mudança da P3.9.
 *       Lista vazia = minutas ainda não aprovadas, e a visão diz isso
 *       pelo estado da seção, não por uma lista inventada;</li>
 *   <li><b>Certificações do provedor (P0.26):</b> são do PROVEDOR de
 *       hospedagem, não do Ziip SAN — a visão carrega provedor, região e
 *       a distinção entre certificações e relatórios de asseguração
 *       exatamente como a P0.26 os registra, com a fonte citada;</li>
 *   <li><b>Pentest (LC.8):</b> sem resumo entregue pela fonte, a seção é
 *       {@code PENDING_SOURCE} — a LC.8 roda sobre a produção na AWS e
 *       ainda não aconteceu; publicar um "resumo" agora seria inventar
 *       um resultado de auditoria;</li>
 *   <li><b>Encarregado (Res. CD/ANPD 18/2024):</b> sem nome/canal
 *       registrados na fonte, a seção é {@code PENDING_SOURCE}. (O
 *       encarregado de cada TENANT é outro dado: vive em
 *       {@code lgpd.dpo_contact} e é exibido no portal pela P3.40.);</li>
 *   <li><b>ASVS (P3.8, PLT-08):</b> o critério é fato de spec — ASVS 5.0,
 *       nível 2 em geral e nível 3 em autenticação, pagamento e dados
 *       pessoais — e a visão o declara como CRITÉRIO; as evidências
 *       (matriz requisito/teste/evidência gerada no CI) só viram seção
 *       {@code AVAILABLE} quando a fonte as entregar;</li>
 *   <li><b>Divulgação responsável:</b> reflete a
 *       {@link DisclosurePolicy} real — sem canal/prazo decididos, a
 *       seção é {@code PENDING_DECISION} e nenhum prazo é exibido.</li>
 * </ul>
 *
 * <p>Nenhum dado de relato ou de pesquisador existe nesta visão: o Trust
 * Center é institucional; o fluxo de relatos é interno (Central).
 * JSON somente pela classe {@code Json} do core ({@link #toJson}).
 */
public final class TrustCenterService {

    /** Estado público de uma seção: fato com fonte, ou pendência declarada. */
    public enum SectionState {
        AVAILABLE, PENDING_SOURCE, PENDING_DECISION
    }

    /** Visão pública de um suboperador listado. */
    public record SubprocessorView(String name, String service, String purpose, String country,
                                   boolean processesPersonalData, boolean changeAnnounced,
                                   Instant changeEffectiveAt) {
    }

    /**
     * Certificação/relatório DO PROVEDOR de hospedagem (não do Ziip SAN).
     * A separação entre {@code certifications} e {@code assuranceReports}
     * é a da P0.26: ISO são certificações; SOC são relatórios de
     * asseguração de auditor independente — misturar os dois seria
     * afirmar uma certificação que não existe.
     */
    public record ProviderCertification(String provider, String region,
                                        List<String> certifications, List<String> assuranceReports,
                                        String sourceRef) {
        public ProviderCertification {
            certifications = certifications == null ? List.of() : List.copyOf(certifications);
            assuranceReports = assuranceReports == null ? List.of() : List.copyOf(assuranceReports);
        }
    }

    /** Seção com estado: conteúdo quando AVAILABLE; nota honesta quando pendente. */
    public record SectionView(SectionState state, String note) {
    }

    /** Seção do pentest: resumo só existe quando a LC.8 o entregar. */
    public record PentestView(SectionState state, String summary, String note) {
    }

    /** Seção do encarregado da plataforma (o do tenant é da P3.40, no portal). */
    public record DpoView(SectionState state, String name, String contact, String note) {
    }

    /** Seção de divulgação responsável: espelha a política configurada, sem inventar prazo. */
    public record DisclosureView(SectionState state, String contactUri, String policyUrl,
                                 String acknowledgementTarget, String note) {
    }

    /** A página inteira, como o endpoint a devolve. */
    public record TrustCenterView(Instant generatedAt,
                                  List<SubprocessorView> subprocessors,
                                  List<SubprocessorView> announcedChanges,
                                  SectionView subprocessorsSection,
                                  List<ProviderCertification> providerCertifications,
                                  SectionView providerCertificationsSection,
                                  PentestView pentest,
                                  String statusPagePath,
                                  SectionView privacyAndRetention,
                                  DpoView dpo,
                                  String securityQuestionnairePath,
                                  SectionView asvsEvidence,
                                  DisclosureView disclosure) {
    }

    /** Caminho da página de status da P3.18 na mesma hospedagem estática. */
    public static final String STATUS_PAGE_PATH = "../status-page/index.html";

    /** Resposta padrão a questionários de segurança, publicada junto da página. */
    public static final String SECURITY_QUESTIONNAIRE_PATH = "questionario-seguranca-respostas.md";

    /** Resumo público de privacidade e retenção, publicado junto da página. */
    public static final String PRIVACY_AND_RETENTION_PATH = "privacidade-e-retencao.md";

    /** Critério ASVS da P3.8 (fato de spec, não resultado): nível 2 geral; nível 3 em autenticação, pagamento e dados pessoais. */
    public static final String ASVS_CRITERION_NOTE =
            "Critério de aceite da P3.8 (PLT-08): OWASP ASVS 5.0, nível 2 em geral e nível 3 em "
                    + "autenticação, pagamento e dados pessoais. As evidências são a matriz "
                    + "requisito/teste/evidência gerada no CI pela P3.8.";

    private TrustCenterService() {
    }

    public static TrustCenterView buildView(List<Subprocessor> subprocessors,
                                            List<ProviderCertification> providerCertifications,
                                            String pentestSummary,
                                            String dpoName, String dpoContact,
                                            DisclosurePolicy disclosurePolicy,
                                            boolean asvsEvidenceAvailable,
                                            Instant now) {
        Objects.requireNonNull(subprocessors, "Lista de suboperadores é obrigatória (vazia, não nula)");
        Objects.requireNonNull(providerCertifications, "Lista de certificações é obrigatória (vazia, não nula)");
        Objects.requireNonNull(disclosurePolicy, "Política de divulgação é obrigatória");
        Objects.requireNonNull(now, "now é obrigatório");

        List<Subprocessor> listed = subprocessors.stream().filter(Subprocessor::isPubliclyListed).toList();
        List<SubprocessorView> subViews = listed.stream()
                .filter(s -> s.changeStatus() != Subprocessor.ChangeStatus.CHANGE_ANNOUNCED)
                .map(TrustCenterService::toView).toList();
        List<SubprocessorView> changeViews = listed.stream()
                .filter(s -> s.changeStatus() == Subprocessor.ChangeStatus.CHANGE_ANNOUNCED)
                .map(TrustCenterService::toView).toList();
        SectionView subSection = listed.isEmpty()
                ? new SectionView(SectionState.PENDING_SOURCE,
                        "Lista de suboperadores pendente: as minutas da P3.9 ainda não foram aprovadas pelo piloto; nenhum suboperador é exibido antes dessa aprovação.")
                : new SectionView(SectionState.AVAILABLE, null);

        SectionView certSection = providerCertifications.isEmpty()
                ? new SectionView(SectionState.PENDING_SOURCE,
                        "Relatórios do provedor pendentes: o relatório de certificações arquivado na P0.44 (fonte P0.26) ainda não foi entregue a esta página.")
                : new SectionView(SectionState.AVAILABLE, null);

        PentestView pentest = (pentestSummary == null || pentestSummary.isBlank())
                ? new PentestView(SectionState.PENDING_SOURCE, null,
                        "Resumo do pentest pendente: o pentest externo é a LC.8, executado sobre a produção na AWS antes do primeiro dado real; nenhum resultado existe ainda.")
                : new PentestView(SectionState.AVAILABLE, pentestSummary, null);

        DpoView dpo = (dpoName == null || dpoName.isBlank() || dpoContact == null || dpoContact.isBlank())
                ? new DpoView(SectionState.PENDING_SOURCE, null, null,
                        "Encarregado da plataforma pendente de registro (Res. CD/ANPD 18/2024). O encarregado de cada tenant é exibido no portal do tenant (P3.40, fonte lgpd.dpo_contact).")
                : new DpoView(SectionState.AVAILABLE, dpoName, dpoContact, null);

        DisclosureView disclosure;
        if (disclosurePolicy.isPublishable()) {
            disclosure = new DisclosureView(SectionState.AVAILABLE, disclosurePolicy.contactUri(),
                    disclosurePolicy.policyUrl(), disclosurePolicy.acknowledgementTarget().toString(), null);
        } else {
            disclosure = new DisclosureView(SectionState.PENDING_DECISION, null, disclosurePolicy.policyUrl(),
                    null, "Canal oficial e prazo de resposta da divulgação responsável pendentes de decisão do dono; nenhum canal ou prazo é exibido antes dessa decisão.");
        }

        SectionView asvs = asvsEvidenceAvailable
                ? new SectionView(SectionState.AVAILABLE, ASVS_CRITERION_NOTE)
                : new SectionView(SectionState.PENDING_SOURCE,
                        ASVS_CRITERION_NOTE + " Evidências ainda não publicadas nesta página.");

        SectionView privacy = new SectionView(SectionState.AVAILABLE,
                "Resumo público em privacidade-e-retencao.md; a política de privacidade de cada tenant é versionada e publicada no portal do tenant (P3.40).");

        return new TrustCenterView(now, subViews, changeViews, subSection,
                List.copyOf(providerCertifications), certSection, pentest, STATUS_PAGE_PATH,
                privacy, dpo, SECURITY_QUESTIONNAIRE_PATH, asvs, disclosure);
    }

    private static SubprocessorView toView(Subprocessor s) {
        boolean announced = s.changeStatus() == Subprocessor.ChangeStatus.CHANGE_ANNOUNCED;
        return new SubprocessorView(s.name(), s.service(), s.purpose(), s.country(),
                s.processesPersonalData(), announced, announced ? s.effectiveAt() : null);
    }

    /** JSON da visão, SOMENTE pela classe {@code Json} do core. */
    public static String toJson(TrustCenterView view) {
        Objects.requireNonNull(view, "Visão é obrigatória para serializar");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", view.generatedAt().toString());

        Map<String, Object> subs = new LinkedHashMap<>();
        subs.put("state", view.subprocessorsSection().state().name());
        subs.put("note", view.subprocessorsSection().note());
        subs.put("items", view.subprocessors().stream().map(TrustCenterService::subToMap).toList());
        subs.put("announcedChanges", view.announcedChanges().stream().map(TrustCenterService::subToMap).toList());
        root.put("subprocessors", subs);

        Map<String, Object> certs = new LinkedHashMap<>();
        certs.put("state", view.providerCertificationsSection().state().name());
        certs.put("note", view.providerCertificationsSection().note());
        certs.put("items", view.providerCertifications().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider", c.provider());
            m.put("region", c.region());
            m.put("certifications", c.certifications());
            m.put("assuranceReports", c.assuranceReports());
            m.put("sourceRef", c.sourceRef());
            return m;
        }).toList());
        root.put("providerCertifications", certs);

        Map<String, Object> pentest = new LinkedHashMap<>();
        pentest.put("state", view.pentest().state().name());
        pentest.put("summary", view.pentest().summary());
        pentest.put("note", view.pentest().note());
        root.put("pentest", pentest);

        root.put("statusPagePath", view.statusPagePath());

        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("state", view.privacyAndRetention().state().name());
        privacy.put("note", view.privacyAndRetention().note());
        privacy.put("path", PRIVACY_AND_RETENTION_PATH);
        root.put("privacyAndRetention", privacy);

        Map<String, Object> dpo = new LinkedHashMap<>();
        dpo.put("state", view.dpo().state().name());
        dpo.put("name", view.dpo().name());
        dpo.put("contact", view.dpo().contact());
        dpo.put("note", view.dpo().note());
        root.put("dpo", dpo);

        root.put("securityQuestionnairePath", view.securityQuestionnairePath());

        Map<String, Object> asvs = new LinkedHashMap<>();
        asvs.put("state", view.asvsEvidence().state().name());
        asvs.put("note", view.asvsEvidence().note());
        root.put("asvsEvidence", asvs);

        Map<String, Object> disclosure = new LinkedHashMap<>();
        disclosure.put("state", view.disclosure().state().name());
        disclosure.put("contactUri", view.disclosure().contactUri());
        disclosure.put("policyUrl", view.disclosure().policyUrl());
        disclosure.put("acknowledgementTarget", view.disclosure().acknowledgementTarget());
        disclosure.put("note", view.disclosure().note());
        root.put("disclosure", disclosure);

        return Json.write(root);
    }

    private static Map<String, Object> subToMap(SubprocessorView s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("service", s.service());
        m.put("purpose", s.purpose());
        m.put("country", s.country());
        m.put("processesPersonalData", s.processesPersonalData());
        m.put("changeAnnounced", s.changeAnnounced());
        m.put("changeEffectiveAt", s.changeEffectiveAt() == null ? null : s.changeEffectiveAt().toString());
        return m;
    }
}
