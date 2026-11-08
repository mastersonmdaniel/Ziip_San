package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Coorte de domicilios de perfil semelhante (ED-101, CAD-102 consumido da
 * ED-38): os consumos (m3) dos domicilios do MESMO grupo, ja selecionados
 * por quem chama, sob uma chave de grupo opaca.
 *
 * <p><b>O criterio de semelhanca NAO e definido nesta entrega</b> — nenhuma
 * fonte da tarefa enumera os atributos do perfil (categoria, faixa de
 * economias, setor, porte do imovel...). Inventar o criterio seria dado sem
 * fonte: ele e a pendencia nomeada P-ED101-03 do relatorio, a fechar na
 * integracao com o cadastro (P1.3) e o aceite do tenant. Aqui a coorte e
 * consumida como entrada explicita, como a ED-38 consome a dela.
 *
 * <p>Privacidade por construcao: a coorte carrega SO valores de consumo —
 * nenhum identificador de domicilio ou de terceiro entra neste tipo, e o
 * relatorio ({@link PeerReport}) so expoe agregados.
 */
public record PeerCohort(String groupKey, List<BigDecimal> consumptionsM3) {

    public PeerCohort {
        if (groupKey == null || groupKey.isBlank()) {
            throw new EngagementValidationException("COHORT_GROUP_REQUIRED",
                    "chave do grupo de pares e obrigatoria");
        }
        Objects.requireNonNull(consumptionsM3, "consumos da coorte sao obrigatorios");
        for (BigDecimal v : consumptionsM3) {
            if (v == null || v.signum() < 0) {
                throw new EngagementValidationException("COHORT_VALUE_INVALID",
                        "consumo da coorte deve ser nao-negativo: " + v);
            }
        }
        consumptionsM3 = List.copyOf(consumptionsM3);
    }

    public int size() {
        return consumptionsM3.size();
    }
}
