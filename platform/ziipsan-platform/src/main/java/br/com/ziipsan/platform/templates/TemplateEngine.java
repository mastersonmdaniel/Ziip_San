package br.com.ziipsan.platform.templates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor puro de modelos (P2.30): validação, blocos travados, prévia e
 * renderização — sem banco, sem Spring, sem HTTP. O serviço da aplicação
 * só persiste o que isto aprova e traduz as mensagens para problem+json.
 *
 * <p>Sintaxe de variável: {@code {{nome}}} — nome em minúsculas, dígitos e
 * sublinhado, sem espaços (espaço em branco ao redor do nome é tolerado:
 * {@code {{ nome }}} vale o mesmo). Referência a {@code brand.*} resolve
 * pelo {@link TemplateBrandProvider} quando ligado; sem provedor, é
 * variável desconhecida.
 *
 * <p>Validação do aceite, nesta ordem (a primeira falha basta para recusar,
 * mas o serviço devolve todas as mensagens juntas quando as coleta):
 * <ol>
 *   <li>toda referência do corpo existe no catálogo do tipo de documento;</li>
 *   <li>toda variável declarada existe no catálogo E tem o mesmo tipo;</li>
 *   <li>nenhum bloco novo tem origem SYSTEM (reservada à P2.32);</li>
 *   <li>blocos LOCKED da versão anterior sobrevivem byte a byte na nova.</li>
 * </ol>
 *
 * <p>Renderização: toda referência do corpo precisa de valor (do chamador
 * ou da marca); valor de tipo incompatível recusa a geração. Variável
 * declarada sem referência no corpo não é erro — é variável disponível
 * que o corpo não usou (o autor pode declará-la para a próxima revisão).
 */
public final class TemplateEngine {

    private static final Pattern REF = Pattern.compile("\\{\\{\\s*([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)?)\\s*\\}\\}");

    /** Uma variável declarada numa versão (nome + tipo). */
    public record DeclaredVariable(String name, TemplateVariableType type) {
    }

    private TemplateEngine() {
    }

    /** Texto do modelo: os blocos concatenados, na ordem. */
    public static String bodyText(List<TemplateBlock> blocks) {
        StringBuilder body = new StringBuilder();
        for (TemplateBlock block : blocks) {
            body.append(block.text());
        }
        return body.toString();
    }

    /** Referências {@code {{nome}}} do texto, sem duplicar, na ordem. */
    public static List<String> references(String text) {
        Set<String> refs = new LinkedHashSet<>();
        Matcher matcher = REF.matcher(text == null ? "" : text);
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return List.copyOf(refs);
    }

    /**
     * Valida uma versão contra o catálogo do tipo (regras 1–3). Devolve a
     * lista de problemas; vazia = válida. Não toca em blocos travados (ver
     * {@link #lockedBlockProblems(List, List)}).
     */
    public static List<String> validationProblems(TemplateDocumentType documentType,
                                                  List<TemplateBlock> blocks,
                                                  List<DeclaredVariable> declared) {
        List<String> problems = new ArrayList<>();
        String body = bodyText(blocks);
        for (String ref : references(body)) {
            if (ref.startsWith("brand.")) {
                continue; // resolvida pelo provedor de marca na geração, não pelo catálogo
            }
            if (TemplateVariableCatalog.variable(documentType, ref).isEmpty()) {
                problems.add("Variável desconhecida no corpo: " + ref + ".");
            }
        }
        for (DeclaredVariable variable : declared) {
            var catalog = TemplateVariableCatalog.variable(documentType, variable.name());
            if (catalog.isEmpty()) {
                problems.add("Variável declarada fora do catálogo: " + variable.name() + ".");
            } else if (!catalog.get().type().equals(variable.type())) {
                problems.add("Variável '" + variable.name() + "' declarada como " + variable.type()
                        + ", mas o catálogo a define como " + catalog.get().type() + ".");
            }
        }
        for (TemplateBlock block : blocks) {
            if (block.origin() == TemplateBlock.Origin.SYSTEM) {
                problems.add("Bloco '" + block.id() + "' com origem SYSTEM: o conteúdo legal"
                        + " obrigatório vem da P2.32 e não pode ser criado por esta frente.");
            }
        }
        return problems;
    }

    /**
     * Valida os blocos travados (regra 4): todo bloco LOCKED da versão
     * anterior tem de existir na nova com o mesmo texto. Bloco LOCKED
     * removido ou alterado é problema; bloco FREE pode mudar livremente, e
     * bloco LOCKED novo (sem anterior) é aceito — é assim que o tenant
     * trava um trecho próprio antes da P2.32.
     */
    public static List<String> lockedBlockProblems(List<TemplateBlock> previous, List<TemplateBlock> next) {
        List<String> problems = new ArrayList<>();
        Map<String, TemplateBlock> nextById = new LinkedHashMap<>();
        for (TemplateBlock block : next) {
            nextById.put(block.id(), block);
        }
        for (TemplateBlock old : previous) {
            if (!old.locked()) {
                continue;
            }
            TemplateBlock kept = nextById.get(old.id());
            if (kept == null) {
                problems.add("Bloco travado removido: " + old.id() + ".");
            } else if (!kept.text().equals(old.text())) {
                problems.add("Bloco travado alterado: " + old.id() + ".");
            } else if (!kept.locked()) {
                problems.add("Bloco travado destravado: " + old.id() + ".");
            }
        }
        return problems;
    }

    /**
     * Renderiza o modelo com os valores dados. Valores de marca (chaves
     * {@code brand.*}) só preenchem referências sem valor do chamador. Toda
     * referência precisa de valor; valor incompatível com o tipo do
     * catálogo (ou com {@code brand.*}, que é sempre TEXT) recusa a geração
     * com {@link IllegalArgumentException}.
     */
    public static String render(TemplateDocumentType documentType, List<TemplateBlock> blocks,
                                Map<String, String> values, TemplateBrandProvider brandProvider,
                                java.util.UUID tenantId) {
        Map<String, String> merged = new LinkedHashMap<>(values);
        if (brandProvider != null) {
            for (Map.Entry<String, String> brand : brandProvider.brandVariables(tenantId).entrySet()) {
                merged.putIfAbsent(brand.getKey(), brand.getValue());
            }
        }
        String body = bodyText(blocks);
        Matcher matcher = REF.matcher(body);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String ref = matcher.group(1);
            String raw = merged.get(ref);
            if (raw == null) {
                throw new IllegalArgumentException("Variável sem valor na geração: " + ref + ".");
            }
            String formatted;
            if (ref.startsWith("brand.")) {
                formatted = TemplateVariableType.TEXT.format(raw, ref);
            } else {
                var catalog = TemplateVariableCatalog.variable(documentType, ref)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Variável desconhecida na geração: " + ref + "."));
                formatted = catalog.type().format(raw, ref);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** Prévia com massa sintética: renderiza com os valores de exemplo do catálogo. */
    public static String preview(TemplateDocumentType documentType, List<TemplateBlock> blocks) {
        Map<String, String> samples = new LinkedHashMap<>(TemplateVariableCatalog.sampleValues(documentType));
        // A prévia não tem marca ligada: referência a brand.* na prévia é erro
        // explícito, não texto vazio — a marca só aparece com a P2.17 ligada.
        return render(documentType, blocks, samples, null, null);
    }
}
