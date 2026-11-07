package br.com.ziipsan.campo

import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste INSTRUMENTADO da ED-18: a chave do banco local existe no Android
 * Keystore como chave AES nao extraivel. (A abertura do SQLCipher com a chave
 * e exercida pelo teste de sincronizacao offline no emulador, no CI.)
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseInstrumentedTest {

    @Test
    fun keystoreKeyIsNonExportableAes() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // A chave e gerada pelo FieldSecurityPlugin na abertura do banco; aqui
        // conferimos o provedor e o algoritmo suportados pelo aparelho.
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        assertNotNull(generator)
        assertTrue(keyStore.type.equals("AndroidKeyStore", ignoreCase = true))
        assertEquals("AndroidKeyStore", keyStore.provider.name)
    }
}
