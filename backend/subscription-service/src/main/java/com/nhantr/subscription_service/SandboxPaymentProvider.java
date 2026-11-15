package com.nhantr.subscription_service;

import com.nhantr.common.api.ApiException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Deterministic local sandbox adapter. No real payment provider or real charges. */
public class SandboxPaymentProvider {
    private final String checkoutBase;
    private final String secret;
    private final boolean enabled;
    public SandboxPaymentProvider(String checkoutBase, String secret) {
        this(checkoutBase,secret,true);
    }
    public SandboxPaymentProvider(String checkoutBase,String secret,boolean enabled) {
        this.enabled=enabled;
        URI uri=URI.create(checkoutBase);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null || secret==null || secret.length()<32) {
            throw new IllegalArgumentException("Sandbox requires a configured HTTPS URL and >=32 character signing key.");
        }
        this.checkoutBase=checkoutBase.replaceAll("/+$", ""); this.secret=secret;
    }
    public boolean enabled() { return enabled; }
    public String checkoutUrl(String id) { return checkoutBase+"/"+id; }
    public String sign(String body, Instant at) {
        String timestamp=Long.toString(at.getEpochSecond());
        return "t="+timestamp+",v1="+digest(timestamp+"."+body);
    }
    public void verify(String body, String signature, Instant now) {
        try {
            if (signature==null || signature.length()>256) throw new IllegalArgumentException();
            String[] parts=signature.split(",",-1);
            if (parts.length!=2 || !parts[0].startsWith("t=") || !parts[1].startsWith("v1=")) throw new IllegalArgumentException();
            long at=Long.parseLong(parts[0].substring(2));
            if (at<now.getEpochSecond()-300 || at>now.getEpochSecond()+300) throw new IllegalArgumentException();
            byte[] supplied=HexFormat.of().parseHex(parts[1].substring(3));
            byte[] expected=HexFormat.of().parseHex(digest(at+"."+body));
            if (!MessageDigest.isEqual(expected,supplied)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_PAYMENT_SIGNATURE", "Payment signature is invalid or expired.");
        }
    }
    private String digest(String message) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("HMAC unavailable",e); }
    }
}
