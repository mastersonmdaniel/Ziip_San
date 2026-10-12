package br.com.sigsan.platform.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleRegistry {

    private final Map<String, ModuleDescriptor> modules = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> moduleDependencies = new ConcurrentHashMap<>();
    private final Map<String, ModuleStatus> moduleStatus = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        registerCoreModules();
        validateDependencies();
        log.info("Module Registry initialized with {} modules", modules.size());
    }

    private void registerCoreModules() {
        // COMMERCIAL CORE
        register(ModuleDescriptor.builder()
                .id("commercial-core")
                .name("Commercial Core")
                .version("1.0.0")
                .category(ModuleCategory.COMMERCIAL)
                .description("Faturamento, Arrecadação, Cobrança, Contábil, Portal")
                .dependencies(Set.of("platform-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("billing")
                .name("Faturamento")
                .version("1.0.0")
                .category(ModuleCategory.COMMERCIAL)
                .description("Geração de faturas, tarifas, retificações, contas finais")
                .dependencies(Set.of("commercial-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("collection")
                .name("Arrecadação")
                .version("1.0.0")
                .category(ModuleCategory.COMMERCIAL)
                .description("FEBRABAN, PIX, Vans, Conciliação bancária")
                .dependencies(Set.of("commercial-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("dunning")
                .name("Cobrança")
                .version("1.0.0")
                .category(ModuleCategory.COMMERCIAL)
                .description("Parcelamento, corte/religação, SPC/Serasa, Dívida Ativa")
                .dependencies(Set.of("commercial-core", "billing"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("accounting")
                .name("Contábil")
                .version("1.0.0")
                .category(ModuleCategory.COMMERCIAL)
                .description("e-Sfinge SC, Lançamentos, PDD, Integração ERP")
                .dependencies(Set.of("commercial-core", "billing", "collection"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("portal")
                .name("Portal & App Cliente")
                .version("1.0.0")
                .category(ModuleCategory.COMMERCIAL)
                .description("Autoatendimento, 2a via, PIX, Chat, Solicitações")
                .dependencies(Set.of("commercial-core"))
                .required(true)
                .build());

        // OPERATIONS CORE
        register(ModuleDescriptor.builder()
                .id("operations-core")
                .name("Operações Core")
                .version("1.0.0")
                .category(ModuleCategory.OPERATIONS)
                .description("OS, Hidrometria, Leitura, Equipamentos, Frota")
                .dependencies(Set.of("platform-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("field-operations")
                .name("Ordens de Serviço Mobile")
                .version("1.0.0")
                .category(ModuleCategory.OPERATIONS)
                .description("OS Offline, Formulários, GPS, Fotos, Checklists")
                .dependencies(Set.of("operations-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("metering")
                .name("Hidrometria")
                .version("1.0.0")
                .category(ModuleCategory.OPERATIONS)
                .description("Ciclo vida hidrômetro, AMR, Telemetria, Alertas")
                .dependencies(Set.of("operations-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("reading")
                .name("Leitura & Consumo")
                .version("1.0.0")
                .category(ModuleCategory.OPERATIONS)
                .description("Roteiros, Massa leitura, Crítica IA, Roteirização")
                .dependencies(Set.of("operations-core", "metering"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("assets")
                .name("Ativos & Frota")
                .version("1.0.0")
                .category(ModuleCategory.OPERATIONS)
                .description("Cadastro equipamentos, Manutenção preventiva, Frota")
                .dependencies(Set.of("operations-core"))
                .required(false)
                .build());

        register(ModuleDescriptor.builder()
                .id("maintenance")
                .name("Manutenção Ativos")
                .version("1.0.0")
                .category(ModuleCategory.OPERATIONS)
                .description("ISO 55001, RCM, Planos preventiva, MTBF/MTTR")
                .dependencies(Set.of("operations-core", "assets"))
                .required(false)
                .build());

        // INTELLIGENCE
        register(ModuleDescriptor.builder()
                .id("intelligence-core")
                .name("Intelligence Core")
                .version("1.0.0")
                .category(ModuleCategory.INTELLIGENCE)
                .description("Perdas, Qualidade, GIS, IA, BI")
                .dependencies(Set.of("platform-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("losses")
                .name("Gestão de Perdas")
                .version("1.0.0")
                .category(ModuleCategory.INTELLIGENCE)
                .description("Balanço IWA/AWWA, DMC, Setorização, IA Vazamento, EPANET")
                .dependencies(Set.of("intelligence-core", "metering", "reading"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("quality")
                .name("Qualidade Água")
                .version("1.0.0")
                .category(ModuleCategory.INTELLIGENCE)
                .description("SCADA, myLIMS, Portaria MS 888, Cloro/pH/Turbidez tempo real")
                .dependencies(Set.of("intelligence-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("gis")
                .name("SIG/GIS")
                .version("1.0.0")
                .category(ModuleCategory.INTELLIGENCE)
                .description("PostGIS, QGIS Server, MapLibre, EPANET WASM, Simulação")
                .dependencies(Set.of("intelligence-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("bi-analytics")
                .name("BI & Analytics")
                .version("1.0.0")
                .category(ModuleCategory.INTELLIGENCE)
                .description("Superset, Evidence.dev, SINISA, Dashboards, OLAP")
                .dependencies(Set.of("intelligence-core"))
                .required(false)
                .build());

        register(ModuleDescriptor.builder()
                .id("fraud-ai")
                .name("Fraude & IA Consumo")
                .version("1.0.0")
                .category(ModuleCategory.INTELLIGENCE)
                .description("Score fraude, Consumo anômalo, Predição vazamento")
                .dependencies(Set.of("intelligence-core", "metering", "reading", "losses"))
                .required(false)
                .build());

        // GRS - GESTÃO DE RESÍDUOS SÓLIDOS
        register(ModuleDescriptor.builder()
                .id("grs-core")
                .name("Gestão Resíduos Sólidos Core")
                .version("1.0.0")
                .category(ModuleCategory.GRS)
                .description("MTR, PGRS, Coleta, Destinação, SINIR")
                .dependencies(Set.of("platform-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("mtr")
                .name("MTR Eletrônico")
                .version("1.0.0")
                .category(ModuleCategory.GRS)
                .description("Manifesto Transporte Resíduos, Assinatura Gov.br, SINIR")
                .dependencies(Set.of("grs-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("pgrs")
                .name("PGRS - Plano Gestão Resíduos")
                .version("1.0.0")
                .category(ModuleCategory.GRS)
                .description("Wizard PGRS, Licenças, Relatórios anuais, CADRI")
                .dependencies(Set.of("grs-core", "mtr"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("collection-waste")
                .name("Coleta & Roteirização Resíduos")
                .version("1.0.0")
                .category(ModuleCategory.GRS)
                .description("Roteirização VRP, App Motorista Offline, Checklist")
                .dependencies(Set.of("grs-core", "mtr"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("disposal")
                .name("Destinação & Tratamento")
                .version("1.0.0")
                .category(ModuleCategory.GRS)
                .description("CDF Automático, Almoxarifado, Logística Reversa, Relatórios SINIR")
                .dependencies(Set.of("grs-core", "mtr"))
                .required(true)
                .build());

        // COMPLIANCE
        register(ModuleDescriptor.builder()
                .id("lgpd")
                .name("LGPD Engine")
                .version("1.0.0")
                .category(ModuleCategory.COMPLIANCE)
                .description("Consentimento, DPIA Auto, Anonimização, Direitos Titular, Auditoria")
                .dependencies(Set.of("platform-core"))
                .required(true)
                .build());

        register(ModuleDescriptor.builder()
                .id("integration")
                .name("Integrações Regulatórias")
                .version("1.0.0")
                .category(ModuleCategory.COMPLIANCE)
                .description("SNIS/SINISA, e-Sfinge SC, Reforma Tributária, ERP Integration")
                .dependencies(Set.of("platform-core", "billing", "accounting"))
                .required(true)
                .build());
    }

    private void register(ModuleDescriptor descriptor) {
        modules.put(descriptor.id(), descriptor);
        moduleDependencies.put(descriptor.id(), descriptor.dependencies());
        moduleStatus.put(descriptor.id(), ModuleStatus.NOT_INSTALLED);
        log.debug("Registered module: {} ({})", descriptor.name(), descriptor.id());
    }

    private void validateDependencies() {
        for (ModuleDescriptor module : modules.values()) {
            for (String dep : module.dependencies()) {
                if (!modules.containsKey(dep)) {
                    log.warn("Module {} depends on missing module: {}", module.id(), dep);
                }
            }
        }
    }

    public ModuleDescriptor getModule(String moduleId) {
        return modules.get(moduleId);
    }

    public List<ModuleDescriptor> getAllModules() {
        return new ArrayList<>(modules.values());
    }

    public List<ModuleDescriptor> getModulesByCategory(ModuleCategory category) {
        return modules.values().stream()
                .filter(m -> m.category() == category)
                .toList();
    }

    public List<ModuleDescriptor> getRequiredModules() {
        return modules.values().stream()
                .filter(ModuleDescriptor::required)
                .toList();
    }

    public ModuleStatus getModuleStatus(String moduleId) {
        return moduleStatus.getOrDefault(moduleId, ModuleStatus.NOT_INSTALLED);
    }

    public void setModuleStatus(String moduleId, ModuleStatus status) {
        moduleStatus.put(moduleId, status);
    }

    public List<String> getInstallationOrder(List<String> moduleIds) {
        // Topological sort for dependency resolution
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (String id : moduleIds) {
            inDegree.put(id, 0);
            adjList.put(id, new ArrayList<>());
        }

        for (String id : moduleIds) {
            ModuleDescriptor module = modules.get(id);
            if (module != null) {
                for (String dep : module.dependencies()) {
                    if (moduleIds.contains(dep)) {
                        adjList.get(dep).add(id);
                        inDegree.put(id, inDegree.getOrDefault(id, 0) + 1);
                    }
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (String id : moduleIds) {
            if (inDegree.get(id) == 0) {
                queue.add(id);
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);

            for (String neighbor : adjList.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (order.size() != moduleIds.size()) {
            throw new IllegalStateException("Circular dependency detected in modules: " + moduleIds);
        }

        return order;
    }

    public boolean isModuleInstalled(String moduleId, String tenantSchema) {
        // Check if module is installed in tenant schema
        return false; // Implementation depends on tenant schema metadata
    }

    public record ModuleDescriptor(
            String id,
            String name,
            String version,
            ModuleCategory category,
            String description,
            Set<String> dependencies,
            boolean required,
            Map<String, Object> config
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String version = "1.0.0";
            private ModuleCategory category;
            private String description;
            private Set<String> dependencies = Set.of();
            private boolean required = false;
            private Map<String, Object> config = Map.of();

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder version(String version) { this.version = version; return this; }
            public Builder category(ModuleCategory category) { this.category = category; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder dependencies(Set<String> dependencies) { this.dependencies = dependencies; return this; }
            public Builder required(boolean required) { this.required = required; return this; }
            public Builder config(Map<String, Object> config) { this.config = config; return this; }
            public ModuleDescriptor build() {
                return new ModuleDescriptor(id, name, version, category, description, dependencies, required, config);
            }
        }
    }

    public enum ModuleCategory {
        PLATFORM, COMMERCIAL, OPERATIONS, INTELLIGENCE, GRS, COMPLIANCE;
    }

    public enum ModuleStatus {
        NOT_INSTALLED, INSTALLING, INSTALLED, FAILED, UPDATING, DISABLED;
    }
}