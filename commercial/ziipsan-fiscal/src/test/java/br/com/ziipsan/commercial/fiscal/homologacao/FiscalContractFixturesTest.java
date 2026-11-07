package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os vetores gravados em fiscal-contract-fixtures obedecem ao §5: sem
 * assinatura, sem elemento Signature, sem certificado. Se um XML da
 * homologação real for copiado para cá por engano, este teste reprova.
 *
 * <p><b>Correção D2 da revisão:</b> o teste ENUMERA o diretório de fixtures
 * (todos os {@code *.xml}, via filesystem) — não lê uma lista fixa de nomes.
 * Uma fixture nova colocada no diretório é conferida automaticamente, e uma
 * fixture nova CONTAMINADA reprova o teste sem ninguém editar lista nenhuma
 * (provado pelo caso da contaminação em diretório temporário, abaixo). O
 * diretório é localizado pelo classpath (o surefire expõe
 * {@code src/test/resources} como diretório) e, como reserva, pelo caminho-fonte
 * a partir do diretório de trabalho — enumeração por filesystem nos dois casos.
 */
class FiscalContractFixturesTest {

    private static final String THUMB_ZIIP_TESTE = "TESTE-THUMBPRINT-ZIIP-0001";

    /** Localiza o diretório fiscal-contract-fixtures no filesystem (classpath ou fonte). */
    private static Path diretorioFixtures() {
        try {
            URL url = FiscalContractFixturesTest.class.getResource("/fiscal-contract-fixtures");
            if (url != null && "file".equals(url.getProtocol())) {
                Path peloClasspath = Paths.get(url.toURI());
                if (Files.isDirectory(peloClasspath)) {
                    return peloClasspath;
                }
            }
        } catch (Exception ignorado) {
            // Sem diretório no classpath: cai para o caminho-fonte abaixo.
        }
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = base; p != null; p = p.getParent()) {
            Path direto = p.resolve("src/test/resources/fiscal-contract-fixtures");
            if (Files.isDirectory(direto)) {
                return direto;
            }
            Path peloModulo = p.resolve("commercial/ziipsan-fiscal/src/test/resources/fiscal-contract-fixtures");
            if (Files.isDirectory(peloModulo)) {
                return peloModulo;
            }
        }
        return null;
    }

    /** Todos os {@code *.xml} do diretório, em ordem de nome — a enumeração do D2. */
    private static List<Path> enumerarXmls(Path diretorio) throws IOException {
        try (Stream<Path> stream = Files.list(diretorio)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
    }

    /** Exige a forma do §5 de TODOS os XMLs enumerados no diretório. */
    private static void exigirDiretorioSeguro(Path diretorio) throws IOException {
        List<Path> xmls = enumerarXmls(diretorio);
        if (xmls.isEmpty()) {
            throw new AssertionError("Diretório de fixtures sem nenhum XML enumerado: " + diretorio);
        }
        for (Path arquivo : xmls) {
            String xml = Files.readString(arquivo, StandardCharsets.UTF_8);
            try {
                VetorReferencia.exigirSeguroParaFixture(xml, THUMB_ZIIP_TESTE);
            } catch (RuntimeException e) {
                throw new AssertionError("Fixture contaminada em " + arquivo.getFileName() + ": " + e.getMessage(), e);
            }
            if (!xml.contains("<tpAmb>2</tpAmb>")) {
                throw new AssertionError("Fixture de homologação não é tpAmb=2: " + arquivo.getFileName());
            }
            if (!xml.contains("SINTETICO")) {
                throw new AssertionError("Fixture não é sintética declarada: " + arquivo.getFileName());
            }
        }
    }

    @Test
    @DisplayName("o diretório de fixtures é ENUMERADO e toda fixture é vetor seguro: tpAmb=2, sem Signature, sem certificado")
    void fixturesSaoVetoresSeguros() throws IOException {
        Path diretorio = diretorioFixtures();
        assertThat(diretorio).as("diretório fiscal-contract-fixtures localizado (classpath ou fonte)").isNotNull();
        List<Path> xmls = enumerarXmls(diretorio);
        // A enumeração não é vazia nem perdeu os vetores conhecidos — sem isto,
        // um diretório errado "passaria" o teste sem conferir nada.
        assertThat(xmls).as("fixtures enumeradas em %s", diretorio).hasSizeGreaterThanOrEqualTo(2);
        assertThat(xmls).extracting(p -> p.getFileName().toString())
                .contains("nfage-sintetica-sem-assinatura.xml", "nfse-dps-sintetica-sem-assinatura.xml");
        exigirDiretorioSeguro(diretorio);
    }

    @Test
    @DisplayName("D2: fixture NOVA contaminada no diretório reprova a conferência, sem editar lista nenhuma")
    void fixtureNovaContaminadaReprova(@TempDir Path diretorioTemporario) throws IOException {
        String limpo = "<DPS xmlns=\"urn:sintetico-teste\"><infDPS><tpAmb>2</tpAmb>"
                + "<xNome>VETOR SINTETICO</xNome></infDPS></DPS>";
        String contaminado = "<NFAg-e xmlns=\"urn:sintetico-teste\"><infNFAg-e><tpAmb>2</tpAmb>"
                + "<xNome>VETOR SINTETICO</xNome></infNFAg-e>"
                + "<Signature><KeyInfo><X509Certificate>QUANTIDADE</X509Certificate></KeyInfo></Signature></NFAg-e>";
        Files.writeString(diretorioTemporario.resolve("vetor-limpo.xml"), limpo, StandardCharsets.UTF_8);
        // O diretório só com o vetor limpo confere...
        exigirDiretorioSeguro(diretorioTemporario);
        // ...e basta ADICIONAR um XML contaminado (nenhuma lista é editada) para reprovar.
        Files.writeString(diretorioTemporario.resolve("vetor-novo-contaminado.xml"), contaminado, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> exigirDiretorioSeguro(diretorioTemporario))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("vetor-novo-contaminado.xml");
    }
}
