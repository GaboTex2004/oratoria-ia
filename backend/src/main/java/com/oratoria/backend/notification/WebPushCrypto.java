package com.oratoria.backend.notification;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push sin dependencias externas: cifrado del mensaje según RFC 8291 (aes128gcm, RFC 8188)
 * y autenticación del servidor con VAPID según RFC 8292 (JWT ES256). Solo usa el JDK.
 */
public final class WebPushCrypto {
    static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private WebPushCrypto() {}

    public record VapidKeys(String publicKey, String privateKey) {}

    /** Genera un par P-256 en base64url: clave pública sin comprimir (65 bytes) y escalar privado (32 bytes). */
    public static VapidKeys generateVapidKeys() {
        KeyPair pair = newKeyPair();
        return new VapidKeys(B64.encodeToString(encodePoint((ECPublicKey) pair.getPublic())),
                B64.encodeToString(unsigned32(((ECPrivateKey) pair.getPrivate()).getS())));
    }

    /** Cuerpo cifrado listo para enviar con {@code Content-Encoding: aes128gcm}. */
    public static byte[] encrypt(byte[] plaintext, String p256dh, String authSecret) {
        try {
            byte[] uaPublic = decode(p256dh);
            byte[] auth = decode(authSecret);
            if (uaPublic.length != 65 || uaPublic[0] != 4 || auth.length != 16) {
                throw new IllegalArgumentException("Claves de suscripción push inválidas.");
            }
            if (plaintext.length + 1 + 16 > RECORD_SIZE - 86) throw new IllegalArgumentException("Mensaje push demasiado largo.");
            KeyPair ephemeral = newKeyPair();
            byte[] asPublic = encodePoint((ECPublicKey) ephemeral.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(publicKey(uaPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdf(auth, ecdhSecret, keyInfo, 32);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // Un único registro: el delimitador 0x02 marca el último (RFC 8188).
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("No se pudo cifrar la notificación push.", failure);
        }
    }

    /** Cabecera {@code Authorization} VAPID para el origen del endpoint. */
    public static String vapidAuthorization(String endpoint, VapidKeys keys, String subject, Instant now) {
        try {
            URI uri = URI.create(endpoint);
            String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond()
                    + ",\"sub\":\"" + subject.replace("\"", "") + "\"}").getBytes(StandardCharsets.UTF_8));
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(privateKey(decode(keys.privateKey())));
            signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
            String jwt = header + "." + claims + "." + B64.encodeToString(signer.sign());
            return "vapid t=" + jwt + ", k=" + keys.publicKey();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("No se pudo firmar la cabecera VAPID.", failure);
        }
    }

    // RFC 5869 con una sola iteración de expansión (longitudes de hasta 32 bytes).
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("P-256 no disponible en este JDK.", failure);
        }
    }

    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256()));
    }

    static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), p256()));
    }

    static byte[] encodePoint(ECPublicKey key) {
        return concat(new byte[]{4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    static byte[] unsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    static byte[] decode(String base64Url) {
        return B64D.decode(base64Url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }
}
