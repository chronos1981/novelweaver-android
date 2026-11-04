package com.novelweaver.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** The manuscript and WebView profile never contain the remembered API key. */
final class KeyVault {
    private static final String ALIAS="NovelWeaver.Quill.v2";
    private final SharedPreferences prefs;
    KeyVault(Context context){prefs=context.getSharedPreferences("encrypted-quill",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    String load() throws Exception {
        String value=prefs.getString("ciphertext","");if(value.isEmpty())return "";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(value,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    void save(String secret,boolean remember) throws Exception {
        SharedPreferences.Editor edit=prefs.edit();
        if(!remember||secret.isEmpty())edit.clear();
        else {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
            edit.putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP));
            edit.putString("ciphertext",Base64.encodeToString(c.doFinal(secret.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));
        }
        if(!edit.commit())throw new java.io.IOException("The encrypted key could not be saved. Please retry.");
    }
}
