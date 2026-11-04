package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.CadastralChangeService;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitService;
import br.com.ziipsan.commercial.registry.application.CustomerService;
import br.com.ziipsan.commercial.registry.application.InternalOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.3, ADR-0039 item 6: TODA escrita de cadastro passa por OS interna da P1.31. O teste de
 * arquitetura recusa o atalho em dois pontos: nenhum controller do cadastro declara campo de
 * repositório (a escrita não pode pular o serviço) e os construtores {@code @Autowired} dos
 * serviços de cliente e unidade recebem {@link InternalOrderService} e
 * {@link CadastralChangeService} (a escrita não pode pular a OS).
 *
 * <p>Sem banco e sem Spring: varredura de classpath + reflection sobre as classes compiladas.
 */
class RegistryWriteRoutingArchTest {

    @Test
    @DisplayName("nenhum controller do cadastro declara campo de repositório")
    void noControllerDeclaresRepositoryField() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var controllers = scanner.findCandidateComponents("br.com.ziipsan.commercial.registry.web");
        assertThat(controllers).as("controllers do cadastro encontrados").isNotEmpty();
        for (var bean : controllers) {
            Class<?> controller = Class.forName(bean.getBeanClassName());
            for (Field field : controller.getDeclaredFields()) {
                assertThat(field.getType().getName())
                        .as("controller %s não pode tocar em repositório (campo %s)",
                                controller.getSimpleName(), field.getName())
                        .doesNotContain(".repository.")
                        .doesNotEndWith("Repository");
            }
        }
    }

    @Test
    @DisplayName("serviços de cliente e unidade recebem InternalOrderService + CadastralChangeService no @Autowired")
    void servicesWireOrderAndChangeServices() {
        for (Class<?> service : List.of(CustomerService.class, ConsumerUnitService.class)) {
            Constructor<?> autowired = Arrays.stream(service.getDeclaredConstructors())
                    .filter(c -> c.isAnnotationPresent(Autowired.class))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            service.getSimpleName() + " não tem construtor @Autowired"));
            List<Class<?>> params = Arrays.asList(autowired.getParameterTypes());
            assertThat(params).as("%s: @Autowired inclui InternalOrderService", service.getSimpleName())
                    .contains(InternalOrderService.class);
            assertThat(params).as("%s: @Autowired inclui CadastralChangeService", service.getSimpleName())
                    .contains(CadastralChangeService.class);
        }
    }
}
