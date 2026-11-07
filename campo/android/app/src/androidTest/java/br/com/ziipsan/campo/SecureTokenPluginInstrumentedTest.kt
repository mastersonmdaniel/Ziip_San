package br.com.ziipsan.campo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.ziipsan.campo.plugins.SecureTokenPlugin
import com.getcapacitor.PluginMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste INSTRUMENTADO da ED-18 (roda no emulador/aparelho do CI, nao nesta VM):
 * prova estrutural de que o refresh token e inacessivel ao JavaScript — nenhum
 * metodo de ponte devolve token, e os unicos metodos sao guardar, trocar por
 * access token, saber se existe e apagar.
 */
@RunWith(AndroidJUnit4::class)
class SecureTokenPluginInstrumentedTest {

    @Test
    fun noBridgeMethodReturnsRefreshToken() {
        val methods = SecureTokenPlugin::class.java.declaredMethods
            .filter { it.isAnnotationPresent(PluginMethod::class.java) }
            .map { it.name }
            .toSet()
        assertEquals(setOf("storeRefreshToken", "hasRefreshToken", "clear", "getAccessToken"), methods)
        assertFalse(methods.any { it.contains("getRefresh", ignoreCase = true) || it.contains("read", ignoreCase = true) })
    }

    @Test
    fun keystoreIsAvailableOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.provider.name.contains("AndroidKeyStore", ignoreCase = true))
        assertEquals("br.com.ziipsan.campo", context.packageName)
    }
}
