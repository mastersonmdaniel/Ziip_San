package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.HolderSituationService;
import br.com.ziipsan.commercial.registry.application.InternalOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.41, mesma disciplina do RegistryWriteRoutingArchTest (ADR-0039 item 6):
 * o controller novo não toca em repositório (a escrita passa pelo serviço)
 * e o serviço novo recebe InternalOrderService (a escrita passa pela OS).
 */
class HolderSituationArchTest {

    @Test
    @DisplayName("HolderSituationController não declara campo de repositório")
    void controllerDeclaresNoRepositoryField() {
        for (Field field : HolderSituationController.class.getDeclaredFields()) {
            assertThat(field.getType().getName())
                    .as("controller não pode tocar em repositório (campo %s)", field.getName())
                    .doesNotContain(".repository.")
                    .doesNotEndWith("Repository");
        }
    }

    @Test
    @DisplayName("HolderSituationService recebe InternalOrderService no construtor")
    void serviceWiresOrderService() {
        Constructor<?>[] constructors = HolderSituationService.class.getDeclaredConstructors();
        assertThat(constructors).as("HolderSituationService tem um único construtor").hasSize(1);
        List<Class<?>> params = Arrays.asList(constructors[0].getParameterTypes());
        assertThat(params).as("HolderSituationService: construtor inclui InternalOrderService")
                .contains(InternalOrderService.class);
    }
}
