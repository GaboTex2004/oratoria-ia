package com.oratoria.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebPushCryptoTests {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    /** Lado del navegador según RFC 8291: con su clave privada y el secreto auth descifra lo que envió el servidor. */
    private static byte[] decryptAsBrowser(byte[] body, KeyPair browser, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        int recordSize = buffer.getInt();
        assertThat(recordSize).isEqualTo(4096);
        byte[] asPublic = new byte[buffer.get()];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic());
        byte[] keyInfo = WebPushCrypto.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = WebPushCrypto.hkdf(auth, agreement.generateSecret(), keyInfo, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    @Test void theBrowserCanDecryptTheMessage() throws Exception {
        KeyPair browser = WebPushCrypto.newKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] message = "{\"title\":\"Tu resultado está listo\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(message,
                B64.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic())), B64.encodeToString(auth));

        assertThat(decryptAsBrowser(body, browser, auth)).isEqualTo(message);
        // Cada envío usa una clave efímera y un salt nuevos.
        assertThat(WebPushCrypto.encrypt(message, B64.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic())),
                B64.encodeToString(auth))).isNotEqualTo(body);
    }

    @Test void invalidSubscriptionKeysAreRejected() {
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[]{1}, B64.encodeToString(new byte[10]), B64.encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theVapidTokenIsSignedWithTheServerKeyForTheEndpointOrigin() throws Exception {
        WebPushCrypto.VapidKeys keys = WebPushCrypto.generateVapidKeys();
        assertThat(WebPushCrypto.decode(keys.publicKey())).hasSize(65);
        assertThat(WebPushCrypto.decode(keys.privateKey())).hasSize(32);
        Instant now = Instant.parse("2026-11-20T12:00:00Z");

        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc", keys, "mailto:a@b.c", now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + keys.publicKey());
        String[] jwt = header.substring("vapid t=".length(), header.indexOf(", k=")).split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"exp\":" + now.plusSeconds(43200).getEpochSecond()).contains("\"sub\":\"mailto:a@b.c\"");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.publicKey(WebPushCrypto.decode(keys.publicKey())));
        verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
    }
}
