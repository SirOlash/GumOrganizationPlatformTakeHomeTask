package com.Semicolon.service.utils;

import com.Semicolon.exceptions.api.InvalidLogoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

@Component
public class LogoValidator {

    private static final Set<String> ALLOWED = Set.of("image/png","image/jpeg","image/webp");
    private static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024; // 2 MB

    @Value("${logo.max.bytes:2097152}")
    private long maxBytes;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean validateLogoUrl(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) throw new InvalidLogoException("logoUrl is empty");

        try {
            URI uri = URI.create(urlStr.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                throw new InvalidLogoException("logoUrl must use http or https");
            }

            // basic SSRF protection: block loopback / private addresses
            InetAddress addr = InetAddress.getByName(uri.getHost());
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isSiteLocalAddress()) {
                throw new InvalidLogoException("logoUrl resolves to a private or loopback address");
            }

            // HEAD request
            HttpRequest head = HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> headResp = client.send(head, HttpResponse.BodyHandlers.discarding());
            String ct = headResp.headers().firstValue("content-type").orElse("").split(";")[0].trim();
            var clOpt = headResp.headers().firstValue("content-length");

            if (!ct.isEmpty() && !ALLOWED.contains(ct)) {
                throw new InvalidLogoException("logo content-type not allowed: " + ct);
            }
            if (clOpt.isPresent()) {
                long len = Long.parseLong(clOpt.get());
                if (len <= 0 || len > Math.min(maxBytes, DEFAULT_MAX_BYTES)) {
                 }
            }

            // ranged GET to verify magic bytes if needed
            HttpRequest rangeReq = HttpRequest.newBuilder(uri)
                    .header("Range", "bytes=0-1023")
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<InputStream> r = client.send(rangeReq, HttpResponse.BodyHandlers.ofInputStream());
            String ct2 = r.headers().firstValue("content-type").orElse("").split(";")[0].trim();
            if (!ct2.isEmpty() && !ALLOWED.contains(ct2)) {
                throw new InvalidLogoException("logo content-type not allowed: " + ct2);
            }

            try (InputStream in = r.body()) {
                byte[] prefix = in.readNBytes(16);
                if (!looksLikeImage(prefix)) {
                    throw new InvalidLogoException("logo content does not look like an allowed image");
                }
            }

            return true;
        } catch (InvalidLogoException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidLogoException("logo validation failed: " + e.getMessage(), e);
        }
    }

    private boolean looksLikeImage(byte[] b) {
        if (b.length >= 4) {
            // PNG
            if (b[0] == (byte)0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return true;
            // JPEG
            if (b[0] == (byte)0xFF && b[1] == (byte)0xD8 && b[2] == (byte)0xFF) return true;
            // WebP (RIFF....WEBP)
            if (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46) return true;
        }
        return false;
    }
}
