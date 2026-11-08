package br.com.ziipsan.campo

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.ziipsan.campo.billing.FieldBillingFacade
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contrato ED-19 no aparelho/emulador: os vetores
 * billing-contract-fixtures embarcados no artefato ziipsan-billing-rules
 * produzem EXATAMENTE os resultados esperados — os mesmos que o teste JVM
 * do servidor exige. Divergencia aqui bloqueia o merge.
 *
 * PENDENCIA DECLARADA: este teste so roda com Android SDK + emulador
 * (CI de campo / PC do dono); nesta VM ele nao foi executado. O lado JVM
 * dos mesmos vetores foi executado e esta no relatorio da ED-19.
 */
@RunWith(AndroidJUnit4::class)
class BillingRulesContractInstrumentedTest {

    @Test
    fun contractFixturesPassOnDevice() {
        val failures = FieldBillingFacade.runContractFixtures()
        assertTrue("Divergencias dos vetores de contrato no aparelho: $failures", failures.isEmpty())
    }
}
