package br.com.ziipsan.campo.billing

import br.com.ziipsan.commercial.billingrules.FieldBillCalculator
import br.com.ziipsan.commercial.billingrules.FieldBillInput
import br.com.ziipsan.commercial.billingrules.FieldBillResult
import br.com.ziipsan.commercial.billingrules.fixtures.ContractFixtures

/**
 * Fachada Android das regras de campo (ED-19).
 *
 * PONTO CENTRAL DO ACEITE: esta classe NAO calcula nada por conta propria —
 * ela chama o [FieldBillCalculator] do artefato `ziipsan-billing-rules`, o
 * MESMO JAR que o servidor usa (hash conferido na montagem da rota e no
 * build, ver campo/billing-rules.gradle). Nao existe segunda implementacao
 * da conta em Kotlin: divergencia JVM x Android so pode nascer de artefato
 * diferente (barrado pelo hash) ou de entrada diferente (divergencia de
 * base, tratada no servidor).
 */
object FieldBillingFacade {

    /** Calcula a conta de campo com o artefato compartilhado. */
    fun calculate(input: FieldBillInput): FieldBillResult = FieldBillCalculator.calculate(input)

    /**
     * Roda os vetores de contrato embarcados no artefato e devolve as
     * divergencias (lista vazia = contrato verde). O teste instrumentado
     * chama isto no emulador em todo build.
     */
    fun runContractFixtures(): List<String> = ContractFixtures.runAll(ContractFixtures.load())
}
