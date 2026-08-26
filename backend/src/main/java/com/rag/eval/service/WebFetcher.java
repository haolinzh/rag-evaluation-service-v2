package com.rag.eval.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class WebFetcher {

    private static final Logger log = LoggerFactory.getLogger(WebFetcher.class);

    private final ConfigService config;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public WebFetcher(ConfigService config) {
        this.config = config;
    }

    public String fetchText(String url) {
        try {
            URI uri = validate(url);
            long timeoutMs = config.getInt("web.fetch.timeout-ms", 10000);
            int maxBytes = config.getInt("web.fetch.max-bytes", 2 * 1024 * 1024);

            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "Mozilla/5.0 (compatible; RAGBot/1.0)")
                .GET()
                .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();

            byte[] body = readLimited(response.body(), maxBytes);

            if (contentType.contains("text/html") || path.endsWith(".html") || path.endsWith(".htm")) {
                return extractHtml(body, uri);
            }
            return extractTika(body);
        } catch (Exception e) {
            log.warn("Web fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    private URI validate(String url) {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("缺少主机名");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isPrivateOrReserved(addr)) {
                    throw new IllegalArgumentException("禁止访问内网/保留地址: " + addr.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + host, e);
        }
        return uri;
    }

    private boolean isPrivateOrReserved(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int a = b[0] & 0xff;
            int b1 = b[1] & 0xff;
            if (a == 10 || a == 127 || a == 0) return true;
            if (a == 172 && (b1 & 0xf0) == 16) return true;
            if (a == 192 && b1 == 168) return true;
            if (a == 169 && b1 == 254) return true;
            if (a == 100 && (b1 & 0xc0) == 64) return true;
            return false;
        }
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) return true;
        int first = b[0] & 0xff;
        if ((first & 0xfe) == 0xfc) return true; // fc00::/7 ULA
        return false;
    }

    private byte[] readLimited(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException("响应体超过上限 " + maxBytes + " 字节");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private String extractHtml(byte[] body, URI baseUri) throws Exception {
        try (InputStream in = new ByteArrayInputStream(body)) {
            String text = Jsoup.parse(in, null, baseUri.toString()).body().text();
            return text == null ? "" : text.strip();
        }
    }

    private String extractTika(byte[] body) throws Exception {
        try (InputStream in = new ByteArrayInputStream(body)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            parser.parse(in, handler, metadata);
            return handler.toString().strip();
        }
    }
}
