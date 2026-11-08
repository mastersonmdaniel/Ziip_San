package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.api.ApiException;

import java.util.Objects;
import java.util.UUID;

/**
 * Nivel minimo por tipo de documento e por tenant (aceite da P3.34). Le de
 * {@code sign.min_level.<document_type>} via {@link SigningParameters}.
 * Sem valor vigente, a assinatura e RECUSADA: a spec nao fixa minimo de
 * fabrica justamente porque a definicao cabe a cada ente — presumir SIMPLE
 * aqui seria inventar a regra que a spec recusou presumir.
 */
public final class MinLevelPolicy {

    private final SigningParameters parameters;

    public MinLevelPolicy(SigningParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters e obrigatorio");
    }

    /** Chave completa do parametro de um tipo (para mensagens e catalogo). */
    public static String parameterKey(SignableDocumentType documentType) {
        return "sign.min_level." + documentType.code();
    }

    public SignatureLevel requiredLevel(UUID tenantId, SignableDocumentType documentType) {
        if (tenantId == null || documentType == null) {
            throw new IllegalArgumentException("tenant e tipo de documento sao obrigatorios");
        }
        String raw = parameters.minLevelRaw(tenantId, documentType).orElseThrow(() ->
                new ApiException.Validation("Nivel minimo de assinatura nao definido para este tipo "
                        + "de documento neste tenant (" + parameterKey(documentType) + ", classe d): "
                        + "a Lei 14.063/2020 atribui a definicao a cada ente, entao o servico nao "
                        + "presume nivel — registre o parametro antes de assinar.",
                        java.util.List.of(), java.util.List.of()));
        return SignatureLevel.parseParameterValue(raw);
    }

    /** Recusa quando o nivel pretendido esta abaixo do minimo do tenant para o tipo. */
    public void requireSatisfied(UUID tenantId, SignableDocumentType documentType, SignatureLevel intended) {
        SignatureLevel minimum = requiredLevel(tenantId, documentType);
        if (intended == null || !intended.satisfies(minimum)) {
            throw new ApiException.Validation("Nivel de assinatura " + intended
                    + " abaixo do minimo " + minimum + " definido pelo tenant para "
                    + documentType.code() + " (" + parameterKey(documentType) + ").",
                    java.util.List.of(), java.util.List.of());
        }
    }
}
