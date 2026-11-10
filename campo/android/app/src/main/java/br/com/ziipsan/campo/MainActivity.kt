package br.com.ziipsan.campo

import android.os.Bundle
import br.com.ziipsan.campo.plugins.FieldDatabasePlugin
import br.com.ziipsan.campo.plugins.FieldLocationPlugin
import br.com.ziipsan.campo.plugins.FieldSecurityPlugin
import br.com.ziipsan.campo.plugins.SecureTokenPlugin
import com.getcapacitor.BridgeActivity

/**
 * Casca nativa do app de campo (ED-18): registra SO os plugins proprios de
 * seguranca. Nenhum plugin generico de filesystem/storage e registrado — o JS
 * nao tem caminho para o refresh token nem para a chave do banco.
 *
 * AJUSTE RG2-C-P4 (lote mobile-integracao-p03-p04, frente A — declarado):
 * regista tambem o FieldDatabasePlugin (banco local SQLCipher do
 * LocalStore). Ele nao expoe a passphrase nem a chave — so as quatro
 * operacoes do LocalStore sobre JSON dos DTOs reais.
 *
 * AJUSTE P-MOB-GPS-01 (frente mobile-gps-tempo-real, frente B —
 * declarado): regista tambem o FieldLocationPlugin (localizacao em
 * primeiro plano para a trilha GPS, ED-20). Ele so entrega o fix —
 * nenhuma decisao de trilha vive no nativo.
 */
class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(SecureTokenPlugin::class.java)
        registerPlugin(FieldSecurityPlugin::class.java)
        registerPlugin(FieldDatabasePlugin::class.java)
        registerPlugin(FieldLocationPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
