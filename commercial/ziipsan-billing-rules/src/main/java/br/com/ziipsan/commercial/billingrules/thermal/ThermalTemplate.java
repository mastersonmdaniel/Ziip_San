package br.com.ziipsan.commercial.billingrules.thermal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo termico COMO ELE VEM do motor de modelos da P2.30 (ED-19 nao cria
 * leiaute proprio — JOR-06): tipo de documento, canal, versao vigente e as
 * linhas do corpo com marcadores {@code {{variavel}}} do catalogo de
 * variaveis do tipo. A validacao de variaveis/tipos e do motor (P2.30); aqui
 * o modelo e transportado e renderizado — marcador sem valor na renderizacao
 * e erro explicito, nunca texto cru com {@code {{...}}} impresso na conta.
 *
 * @param templateId id do modelo no motor da P2.30
 * @param version    versao vigente do modelo (a do pacote da rota)
 * @param type       tipo de documento
 * @param channel    ZPL ou CPCL
 * @param bodyLines  linhas do corpo, na ordem de impressao
 */
public record ThermalTemplate(UUID templateId, String version, ThermalDocumentType type,
                              ThermalChannel channel, List<String> bodyLines) {

    public ThermalTemplate {
        Objects.requireNonNull(templateId, "modelo termico exige id (motor P2.30)");
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Modelo termico sem versao");
        }
        Objects.requireNonNull(type, "tipo do documento termico e obrigatorio");
        Objects.requireNonNull(channel, "canal do modelo termico e obrigatorio");
        if (bodyLines == null || bodyLines.isEmpty()) {
            throw new IllegalArgumentException("Modelo termico sem corpo");
        }
        bodyLines = List.copyOf(bodyLines);
    }
}
