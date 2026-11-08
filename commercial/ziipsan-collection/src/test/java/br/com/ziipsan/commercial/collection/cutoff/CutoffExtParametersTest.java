package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ausencia de parametro sem padrao nas fontes = erro explicito nomeando a chave. */
class CutoffExtParametersTest {

    @Test
    @DisplayName("cota diaria ausente: MissingParameterException nomeando cut.daily_order_limit")
    void missingQuota() {
        var params = CutoffExtParameters.fromValues(Map.of());
        assertThatThrownBy(params::dailyOrderLimit)
                .isInstanceOf(MissingParameterException.class)
                .extracting(e -> ((MissingParameterException) e).definitionKey())
                .isEqualTo(CutoffExtParameters.DAILY_ORDER_LIMIT);
    }

    @Test
    @DisplayName("prioridade ausente e modelos ausentes: erros explicitos, nada presumido")
    void missingPriorityAndTemplates() {
        var params = CutoffExtParameters.fromValues(Map.of());
        assertThatThrownBy(params::priorityPolicy).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(params::collectionLetterTemplate).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(params::sewerReleaseTemplate).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(params::rulerCutoffStepCode).isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("D5: calendario por nivel ausente: MissingParameterException nomeando cut.level_schedule")
    void missingLevelSchedule() {
        var params = CutoffExtParameters.fromValues(Map.of());
        assertThatThrownBy(() -> params.levelSchedule(CutoffLevel.VALVE))
                .isInstanceOf(MissingParameterException.class)
                .extracting(e -> ((MissingParameterException) e).definitionKey())
                .isEqualTo(CutoffExtParameters.LEVEL_SCHEDULE);
    }

    @Test
    @DisplayName("D5: modelo da notificacao pos-corte ausente: MissingParameterException nomeando cut.cut_unit_notice_template")
    void missingCutUnitNoticeTemplate() {
        var params = CutoffExtParameters.fromValues(Map.of());
        assertThatThrownBy(params::cutUnitNoticeTemplate)
                .isInstanceOf(MissingParameterException.class)
                .extracting(e -> ((MissingParameterException) e).definitionKey())
                .isEqualTo(CutoffExtParameters.CUT_UNIT_NOTICE_TEMPLATE);
    }

    @Test
    @DisplayName("valores declarados voltam tipados")
    void declaredValues() {
        var params = CutoffExtTestSupport.extParams();
        assertThat(params.dailyOrderLimit()).isEqualTo(2);
        assertThat(params.collectionLetterTemplate()).isEqualTo("CARTA_COBRANCA");
        assertThat(params.rulerCutoffStepCode()).isEqualTo("D60_CORTE");
        assertThat(params.priorityPolicy().order()).hasSize(6);
        assertThat(params.levelSchedule(CutoffLevel.BRANCH).minDaysSincePreviousLevel()).isEqualTo(15);
    }

    @Test
    @DisplayName("cota zero ou negativa recusa")
    void invalidQuota() {
        var params = CutoffExtParameters.fromValues(Map.of(CutoffExtParameters.DAILY_ORDER_LIMIT, "0"));
        assertThatThrownBy(params::dailyOrderLimit).isInstanceOf(IllegalStateException.class);
    }
}
