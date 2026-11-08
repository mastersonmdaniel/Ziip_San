package br.com.ziipsan.core.certification.env14001;

import br.com.ziipsan.core.sanitation.SanitationAspectCatalog;

import java.util.Objects;
import java.util.UUID;

/**
 * Um aspecto ambiental e o seu impacto, declarado pelo tenant
 * (aceite da ED-70), SO com a norma ligada: a ativacao e COMPONENTE
 * do registro e o construtor canonico exige ativacao ligada (padrao
 * reforcado da ED-73, correcao D1) — sem um objeto de ativacao ligada
 * nao ha construcao possivel, por fabrica ou por construtor.
 *
 * <p>Cada aspecto declara: a area (ETA/ETE/rede, do catalogo de
 * fabrica {@link SanitationAspectCatalog} ou declarada), a descricao
 * do aspecto e do impacto em palavras do tenant, a SITUACAO —
 * NORMAL, ANORMAL ou de EMERGENCIA (aceite) — e, quando o template de
 * fabrica usado e o do lodo, a marca {@code sludgeLinked}: o aspecto
 * do lodo NAO registra geracao/destinacao aqui — ele LE os totais da
 * ED-105 via {@link SludgeAspectView} (JOR-16, OPE-25). Aspecto de
 * lodo sem template de lodo (ou template de lodo declarado como
 * aspecto comum) e recusado: a fronteira com a ED-105 nao se cruza
 * por declaracao livre.</p>
 *
 * <p>A significancia NAO mora neste registro: ela e o resultado de
 * {@link SignificanceEvaluation} sobre o criterio configurado pelo
 * tenant ({@link SignificanceCriteria}) — nenhum escore de fabrica e
 * presumido aqui.</p>
 */
public record EnvironmentalAspect(Iso14001Activation activation, UUID id, UUID tenantId,
                                  String templateCode,
                                  SanitationAspectCatalog.AspectArea area,
                                  String aspectDescription, String impactDescription,
                                  Situation situation, boolean sludgeLinked) {

    /** Situacao operacional do aspecto (aceite da ED-70). */
    public enum Situation {
        NORMAL, ABNORMAL, EMERGENCY
    }

    public EnvironmentalAspect {
        Objects.requireNonNull(activation, "activation e obrigatoria (componente do registro)");
        activation.requireEnabled();
        Objects.requireNonNull(id, "id do aspecto e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId do aspecto e obrigatorio");
        Objects.requireNonNull(area, "area do aspecto (ETA/ETE/rede) e obrigatoria");
        if (aspectDescription == null || aspectDescription.isBlank()) {
            throw new IllegalArgumentException("aspectDescription e obrigatoria "
                    + "(descricao do aspecto em palavras do tenant)");
        }
        if (impactDescription == null || impactDescription.isBlank()) {
            throw new IllegalArgumentException("impactDescription e obrigatoria "
                    + "(descricao do impacto em palavras do tenant)");
        }
        Objects.requireNonNull(situation, "situation (normal/anormal/emergencia) e obrigatoria");
        if (templateCode != null) {
            templateCode = templateCode.trim();
            if (templateCode.isBlank()) {
                throw new IllegalArgumentException("templateCode, quando declarado, nao pode ser vazio");
            }
            final String declaredTemplateCode = templateCode;
            SanitationAspectCatalog.AspectTemplate template = SanitationAspectCatalog.byCode(declaredTemplateCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "templateCode fora do catalogo de fabrica sanitation: " + declaredTemplateCode));
            if (template.area() != area) {
                throw new IllegalArgumentException("area " + area + " diverge da area do template "
                        + templateCode + " (" + template.area() + ")");
            }
            if (template.sludgeLinked() != sludgeLinked) {
                throw new IllegalArgumentException("sludgeLinked diverge do template " + templateCode
                        + ": o aspecto do lodo le da ED-105 e nao se declara de outro modo");
            }
        } else if (sludgeLinked) {
            throw new IllegalArgumentException("aspecto do lodo (sludgeLinked) exige o template de "
                    + "lodo do catalogo de fabrica: a geracao/destinacao mora na ED-105");
        }
        aspectDescription = aspectDescription.trim();
        impactDescription = impactDescription.trim();
    }

    /** Aspecto a partir de um template do catalogo de fabrica sanitation. */
    public static EnvironmentalAspect fromTemplate(Iso14001Activation activation, UUID id,
                                                   UUID tenantId, String templateCode,
                                                   Situation situation) {
        SanitationAspectCatalog.AspectTemplate template = SanitationAspectCatalog.byCode(templateCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "templateCode fora do catalogo de fabrica sanitation: " + templateCode));
        return new EnvironmentalAspect(activation, id, tenantId, template.code(), template.area(),
                template.aspectLabel(), template.impactLabel(), situation, template.sludgeLinked());
    }

    /** Aspecto livre do tenant, fora do catalogo de fabrica (nunca o do lodo). */
    public static EnvironmentalAspect declareCustom(Iso14001Activation activation, UUID id,
                                                    UUID tenantId,
                                                    SanitationAspectCatalog.AspectArea area,
                                                    String aspectDescription, String impactDescription,
                                                    Situation situation) {
        return new EnvironmentalAspect(activation, id, tenantId, null, area,
                aspectDescription, impactDescription, situation, false);
    }
}
