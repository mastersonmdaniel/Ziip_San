package br.com.ziipsan.platform.vertical;

import br.com.ziipsan.platform.console.OnboardingChecklist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contrato do checklist de implantação (P1.6, ADR-0037): o conteúdo que o
 * console semeia em {@code platform.onboarding_step} é exatamente o da
 * seção {@code onboarding} do manifesto do pacote sanitation, na mesma
 * ordem. Se o manifesto mudar, este teste quebra antes de o seed divergir
 * da fonte declarada — a lista do código não é uma segunda fonte solta.
 */
class OnboardingManifestTest {

    @Test
    @DisplayName("o checklist do console é exatamente o onboarding do manifesto sanitation")
    void checklistMatchesManifest() {
        VerticalManifest manifest = VerticalManifestLoader.load(
                VerticalManifestTest.repoRoot().resolve("packages/sanitation/vertical.json"));
        List<VerticalManifest.OnboardingStep> fromManifest = manifest.onboardingSteps();
        assertEquals(OnboardingChecklist.STEPS.size(), fromManifest.size(),
                "O manifesto e o checklist do console precisam ter o mesmo número de passos");
        for (int i = 0; i < OnboardingChecklist.STEPS.size(); i++) {
            assertEquals(OnboardingChecklist.STEPS.get(i).key(), fromManifest.get(i).key(),
                    "Passo " + i + " divergente (chave)");
            assertEquals(OnboardingChecklist.STEPS.get(i).title(), fromManifest.get(i).title(),
                    "Passo " + i + " divergente (título)");
        }
    }

    @Test
    @DisplayName("o manifesto sanitation carrega os 9 passos do plano v3.0 (linha 634)")
    void manifestHasNineSteps() {
        VerticalManifest manifest = VerticalManifestLoader.load(
                VerticalManifestTest.repoRoot().resolve("packages/sanitation/vertical.json"));
        assertEquals(9, manifest.onboardingSteps().size());
    }
}
