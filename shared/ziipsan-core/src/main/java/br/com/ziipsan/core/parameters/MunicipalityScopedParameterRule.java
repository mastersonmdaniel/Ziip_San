package br.com.ziipsan.core.parameters;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Resolução de parâmetro com escopo de município (MM-02, decisão item 45).
 *
 * <p>Classe NOVA ao lado da vigente {@link ParameterVersionRule} — nenhum
 * ficheiro entregue é editado. O mecanismo é só o ESCOPO: nenhuma chave de
 * parâmetro nova é criada aqui; quais chaves são municipais é uso futuro
 * dos chamadores, não facto deste pacote.
 *
 * <p>Semântica (espelho do SQL pendente
 * {@code MM-02__parameter_versions_municipality_scope.sql}):
 * <ul>
 *   <li>{@code municipalityIbge == null} numa versão = escopo "empresa"
 *       (comportamento vigente da V1_5);</li>
 *   <li>resolver por (chave, município, instante): vence a versão MUNICIPAL
 *       vigente naquele instante, se existir; senão, a da EMPRESA;</li>
 *   <li>resolver SEM município devolve exactamente o que a regra vigente
 *       devolve sobre as versões de escopo-empresa (delegação em
 *       {@link ParameterVersionRule.Validity#covers(LocalDate)});</li>
 *   <li>a EXCLUDE refeita por escopo garante, no banco, no máximo uma
 *       versão vigente por (tenant, chave, escopo, instante) — por isso a
 *       resolução é determinística dentro de cada escopo.</li>
 * </ul>
 *
 * <p>Pré-condição dos métodos: a lista contém apenas versões da MESMA
 * chave do MESMO tenant (o chamador filtra por tenant+chave, como o
 * serviço vigente filtra por chave). Nada aqui persiste.
 */
public final class MunicipalityScopedParameterRule {

    private MunicipalityScopedParameterRule() {
    }

    /**
     * Uma versão de parâmetro com o seu escopo.
     *
     * @param municipalityIbge código IBGE de 7 dígitos, ou {@code null}
     *                         para o escopo-empresa (vigente)
     * @param valueJson        valor canónico em JSON, como guardado em
     *                         {@code parameter_versions.value}
     * @param validity         vigência fechada/aberta da regra vigente
     */
    public record ScopedVersion(String municipalityIbge, String valueJson,
                                ParameterVersionRule.Validity validity) {
        public ScopedVersion {
            if (validity == null) {
                throw new IllegalArgumentException("vigência é obrigatória");
            }
            if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}")) {
                throw new IllegalArgumentException(
                        "municipality_ibge deve ter 7 dígitos ou ser null (escopo empresa)");
            }
        }

        /** Escopo normalizado, como o COALESCE do SQL: empresa = "". */
        public String scopeKey() {
            return municipalityIbge != null ? municipalityIbge : "";
        }

        public boolean isCompanyScope() {
            return municipalityIbge == null;
        }
    }

    /**
     * Resolve por (município, instante): versão municipal vigente vence;
     * na sua ausência, a da empresa; na ausência de ambas, vazio — o
     * mesmo resultado que a vigente produz quando nada vigora (o erro
     * explícito de parâmetro ausente continua a ser do chamador).
     */
    public static Optional<ScopedVersion> resolve(List<ScopedVersion> versions,
                                                  String municipalityIbge, LocalDate at) {
        if (at == null) {
            throw new IllegalArgumentException("instante é obrigatório");
        }
        if (municipalityIbge != null) {
            if (!municipalityIbge.matches("[0-9]{7}")) {
                throw new IllegalArgumentException("municipality_ibge deve ter 7 dígitos");
            }
            Optional<ScopedVersion> municipal = firstCovering(versions, municipalityIbge, at);
            if (municipal.isPresent()) {
                return municipal;
            }
        }
        return firstCovering(versions, null, at);
    }

    /**
     * Resolução sem município: considera SÓ o escopo-empresa e comporta-se
     * exactamente como a regra vigente sobre essas versões.
     */
    public static Optional<ScopedVersion> resolveCompanyScope(List<ScopedVersion> versions,
                                                              LocalDate at) {
        return resolve(versions, null, at);
    }

    private static Optional<ScopedVersion> firstCovering(List<ScopedVersion> versions,
                                                         String municipalityIbge, LocalDate at) {
        for (ScopedVersion v : versions) {
            boolean sameScope = municipalityIbge == null
                    ? v.isCompanyScope()
                    : municipalityIbge.equals(v.municipalityIbge());
            if (sameScope && v.validity().covers(at)) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    /**
     * Anti-sobreposição POR ESCOPO, delegando a comparação temporal à
     * regra vigente: candidata e existentes só colidem quando o escopo
     * normalizado é o mesmo (empresa×empresa e município×mesmo município
     * colidem; empresa×município e municípios distintos não) — o espelho
     * em memória da EXCLUDE refeita do MM-02.
     */
    public static void validateNoOverlapInScope(ScopedVersion candidate,
                                                List<ScopedVersion> existing) {
        List<ParameterVersionRule.Validity> sameScope = existing.stream()
                .filter(v -> v.scopeKey().equals(candidate.scopeKey()))
                .map(ScopedVersion::validity)
                .toList();
        ParameterVersionRule.validateNoOverlap(candidate.validity(), sameScope);
    }
}
