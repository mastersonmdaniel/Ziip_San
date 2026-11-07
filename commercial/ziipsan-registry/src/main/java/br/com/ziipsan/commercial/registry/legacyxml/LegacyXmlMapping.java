package br.com.ziipsan.commercial.registry.legacyxml;

import br.com.ziipsan.core.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P2.19: declaração do importador de XML autorizados anteriores à primeira
 * produção, em formato declarativo NOS MOLDES do framework de migração da
 * P1.2 (PLT-16) — precisão da correção A5 da revisão: o framework/código
 * da P1.2 não é reutilizado (ele importa linhas de cadastro e o
 * {@code source_kind} dos seus lotes não admite XML); esta é uma
 * declaração PRÓPRIA, neste formato, de que um sistema de origem novo
 * entra como DECLARAÇÃO de mapeamento, não como código novo — o motor que
 * a consome é o {@link LegacyXmlImportService}, genérico sobre qualquer
 * declaração neste formato.
 *
 * <p>A declaração vive em arquivo
 * ({@code legacy/mappings/legacy-xml-v1.json}, em resources) e diz: nome e
 * versão do mapeamento, sistema de origem ({@code sourceSystem} — o sistema
 * antigo do piloto ou o emissor usado no intervalo) e os modelos de
 * documento aceitos ({@code acceptedModels}, posições 21–22 da chave de
 * acesso). O emitente esperado NÃO vive na declaração: é dado do tenant,
 * informado no comando de importação e validado lá (CNPJ pela P1.39) —
 * presumir um emitente na declaração seria inventar o dado do piloto.
 *
 * <p>A leitura do JSON é só pela classe {@code Json} do core (regra do
 * projeto: JSON nunca é montado nem lido à mão).
 */
public record LegacyXmlMapping(String name, String version, String sourceSystem,
                               Set<String> acceptedModels) {

    public LegacyXmlMapping {
        if (name == null || name.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Mapeamento de XML de legado exige name e version.");
        }
        if (sourceSystem == null || sourceSystem.isBlank()) {
            throw new IllegalArgumentException("Mapeamento de XML de legado exige sourceSystem:"
                    + " sem o sistema de origem, não há identidade de lote (nos moldes do PLT-16).");
        }
        if (acceptedModels == null || acceptedModels.isEmpty()) {
            throw new IllegalArgumentException("Mapeamento de XML de legado sem modelos aceitos.");
        }
        for (String model : acceptedModels) {
            if (model == null || !model.matches("\\d{2}")) {
                throw new IllegalArgumentException("Modelo aceito fora do leiaute (2 dígitos): " + model);
            }
        }
        acceptedModels = Set.copyOf(new LinkedHashSet<>(acceptedModels));
    }

    /** A declaração embutida v1 (resources). */
    public static LegacyXmlMapping v1() {
        try (InputStream in = LegacyXmlMapping.class.getResourceAsStream("/legacy/mappings/legacy-xml-v1.json")) {
            if (in == null) {
                throw new IllegalStateException("Recurso /legacy/mappings/legacy-xml-v1.json não encontrado.");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Leitura da declaração. Formato: {@code {"name": "...", "version": "...",
     * "sourceSystem": "...", "acceptedModels": ["55", "75"]}}.
     */
    public static LegacyXmlMapping parse(String json) {
        Map<String, Object> root = Json.readMap(json);
        Object models = root.get("acceptedModels");
        if (!(models instanceof List<?> list)) {
            throw new IllegalArgumentException("Declaração de XML de legado sem a lista acceptedModels.");
        }
        Set<String> accepted = new LinkedHashSet<>();
        for (Object item : list) {
            accepted.add(item == null ? null : String.valueOf(item));
        }
        return new LegacyXmlMapping(stringOf(root.get("name")), stringOf(root.get("version")),
                stringOf(root.get("sourceSystem")), accepted);
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
