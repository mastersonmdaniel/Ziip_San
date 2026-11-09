package br.com.ziipsan.core.regulatory.export;

import br.com.ziipsan.core.certification.external.ExternalProfile;
import br.com.ziipsan.core.certification.external.ExternalResource;
import br.com.ziipsan.core.certification.external.ExternalScope;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Definicao do CONTEUDO concreto do escopo regulatorio do perfil
 * externo REGULATOR_SERVICE_OWNER da ED-75 — a pendencia P-ED75-01,
 * atendida por esta ED-97 (GES-22, JOR-23).
 *
 * <p>O escopo regulatorio do regulador/titular compreende EXATAMENTE:
 * (1) este pacote regulatorio de saida (ED-97); (2) os indicadores da
 * ED-48; (3) o relatorio do art. 54 da NR 11/ANA; (4) a ouvidoria;
 * (5) as interrupcoes. NAO compreende pacote de evidencia ISO
 * ({@code EVIDENCE_PACK}) nem dado pessoal alem do minimo — coerente
 * com a matriz da ED-75 ({@link ExternalScope}), que este tipo NAO
 * altera: ele apenas nomeia o conteudo sobre o mecanismo existente.</p>
 *
 * <p>Correcao R7-B1 (Rodada 7 da auditoria continua): o item (1) acima
 * denota SOMENTE a parte AGREGADA PUBLICA do pacote ED-97. O pacote
 * NOMINAL_RESTRICTED NAO tem recurso externo: {@link #resourceForPackage}
 * devolve vazio para ele e {@link #canRegulatorReadPackage} e falso —
 * o nominal nao e legivel como {@code REGULATORY_DATA} pelo
 * {@code REGULATOR_SERVICE_OWNER} em nenhum ponto do modelo. O registro
 * e a evidencia distinguem o kind pelo mesmo criterio (ver
 * {@link SubmissionRecord#isRestricted} e
 * {@link ExportSubmissionService#evidenceRefsFor(String, ExportPackage.Kind)}).
 * A fiação externa (P-ED97-04, pendente) deve consultar
 * {@code resourceForPackage} e recusar expor pacote sem recurso — a
 * regra esta modelada AQUI, antes da fiação, para que a fiação nao
 * precise inventa-la.</p>
 */
public final class RegulatorExternalScope {

    private RegulatorExternalScope() { }

    /** Itens concretos do escopo regulatorio (ED-97). */
    public enum ScopeItem {
        ED97_REGULATORY_PACKAGE(ExternalResource.REGULATORY_DATA),
        ED48_INDICATORS(ExternalResource.REGULATORY_DATA),
        NR11_ART54_REPORT(ExternalResource.REGULATORY_REPORT),
        OUVIDORIA(ExternalResource.REGULATORY_REPORT),
        INTERRUPTIONS(ExternalResource.REGULATORY_DATA);

        private final ExternalResource resource;
        ScopeItem(ExternalResource resource) { this.resource = resource; }
        public ExternalResource resource() { return resource; }
    }

    public static Set<ScopeItem> items() { return Set.of(ScopeItem.values()); }

    /**
     * Recurso externo de um pacote ED-97 POR KIND (correcao R7-B1).
     * Somente o pacote agregado publico e dado regulatorio legivel
     * ({@code REGULATORY_DATA}); o pacote nominal restrito NAO tem
     * recurso externo — devolve vazio. O item
     * {@code ED97_REGULATORY_PACKAGE} do enum denota, portanto, a parte
     * agregada do pacote, nao o pacote inteiro sem distincao de kind.
     */
    public static Optional<ExternalResource> resourceForPackage(ExportPackage.Kind kind) {
        Objects.requireNonNull(kind);
        return kind == ExportPackage.Kind.AGGREGATE_PUBLIC
                ? Optional.of(ScopeItem.ED97_REGULATORY_PACKAGE.resource())
                : Optional.empty();
    }

    /**
     * Verdadeiro somente se o regulador/titular pode ler um pacote
     * ED-97 deste kind pelo mecanismo da ED-75 (correcao R7-B1):
     * verdadeiro para o agregado publico, falso para o nominal restrito.
     */
    public static boolean canRegulatorReadPackage(ExportPackage.Kind kind) {
        return resourceForPackage(kind)
                .map(r -> ExternalScope.canRead(ExternalProfile.REGULATOR_SERVICE_OWNER, r))
                .orElse(false);
    }

    public static Map<ScopeItem, ExternalResource> mapping() {
        Map<ScopeItem, ExternalResource> m = new java.util.EnumMap<>(ScopeItem.class);
        for (ScopeItem i : ScopeItem.values()) m.put(i, i.resource());
        return Map.copyOf(m);
    }

    /**
     * Verdadeiro somente se TODOS os itens do escopo sao legiveis pelo
     * perfil do regulador na matriz da ED-75 E o escopo NAO inclui
     * evidencia ISO nem dado pessoal — a prova viva da definicao.
     */
    public static boolean isCoherentWithEd75Matrix() {
        ExternalProfile reg = ExternalProfile.REGULATOR_SERVICE_OWNER;
        boolean allReadable = items().stream().allMatch(i -> ExternalScope.canRead(reg, i.resource()));
        boolean noIso = !ExternalScope.canRead(reg, ExternalResource.EVIDENCE_PACK);
        boolean noPersonal = !ExternalScope.canRead(reg, ExternalResource.PERSONAL_DATA);
        return allReadable && noIso && noPersonal;
    }
}
