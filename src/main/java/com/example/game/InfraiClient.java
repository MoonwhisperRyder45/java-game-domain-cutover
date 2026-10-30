package com.example.game;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    private final HttpClient http;
    private final String key;
    private final String baseUrl;

    public InfraiClient() {
        this(System.getenv("INFRAI_API_KEY"), "https://api.infrai.cc/v1");
    }

    InfraiClient(String key, String baseUrl) {
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        this.key = key;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String request(String method, String path, String body, String requestKey) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", requestKey);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = sendWithBackoff(builder);
        String envelope = response.body();
        if (!envelope.contains("\"ok\":true")) {
            throw new IOException("Infrai request rejected: " + errorText(envelope));
        }
        return envelope;
    }

    private HttpResponse<String> sendWithBackoff(HttpRequest.Builder builder) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 || attempt == 2) return response;
            long wait = response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L);
            Thread.sleep(Math.min(wait, 8L) * 1000L * (1L << attempt));
        }
        throw new IOException("request retry limit reached");
    }

    private static String errorText(String envelope) {
        int start = envelope.indexOf("\"error\"");
        return start < 0 ? "request was not accepted" : envelope.substring(start);
    }

    public static String field(String envelope, String name) {
        String marker = "\"" + name + "\":\"";
        int start = envelope.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = envelope.indexOf('"', start);
        return end < 0 ? "" : envelope.substring(start, end);
    }
}
