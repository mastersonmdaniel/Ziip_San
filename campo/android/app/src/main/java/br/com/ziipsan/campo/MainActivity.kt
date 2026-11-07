package br.com.ziipsan.campo

import android.os.Bundle
import br.com.ziipsan.campo.plugins.FieldSecurityPlugin
import br.com.ziipsan.campo.plugins.SecureTokenPlugin
import com.getcapacitor.BridgeActivity

/**
 * Casca nativa do app de campo (ED-18): registra SO os plugins proprios de
 * seguranca. Nenhum plugin generico de filesystem/storage e registrado — o JS
 * nao tem caminho para o refresh token nem para a chave do banco.
 */
class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(SecureTokenPlugin::class.java)
        registerPlugin(FieldSecurityPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
