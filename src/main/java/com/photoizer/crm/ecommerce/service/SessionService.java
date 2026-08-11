package com.photoizer.crm.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Emite e valida sessões de carrinho assinadas (HMAC-SHA256).
 * A sessão é um UUID v4 seguido de assinatura; sessões forjadas ou de outro
 * cliente não passam na validação sem conhecer o segredo do servidor.
 */
@Service
public class SessionService {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final byte[] secret;

    public SessionService(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String emitir() {
        var sessao = UUID.randomUUID().toString();
        return sessao + "." + assinar(sessao);
    }

    public boolean valida(String sessao) {
        if (sessao == null) {
            return false;
        }
        var partes = sessao.split("\\.", 2);
        if (partes.length != 2) {
            return false;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(partes[0]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (uuid.version() != 4) {
            return false;
        }
        var esperado = assinar(partes[0]);
        return MessageDigest.isEqual(
            esperado.getBytes(StandardCharsets.UTF_8),
            partes[1].getBytes(StandardCharsets.UTF_8));
    }

    private String assinar(String valor) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return toHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar a sessão", e);
        }
    }

    private static String toHex(byte[] bytes) {
        var out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
