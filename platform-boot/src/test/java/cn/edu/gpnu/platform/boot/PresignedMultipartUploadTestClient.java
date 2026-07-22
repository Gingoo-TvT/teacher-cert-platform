package cn.edu.gpnu.platform.boot;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PresignedMultipartUploadTestClient {

    static final String BROWSER_ORIGIN = "http://localhost:5173";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private PresignedMultipartUploadTestClient() {
    }

    static List<CompletedPart> putAll(JsonNode init, byte[] content) throws Exception {
        if (!"PRESIGNED_MULTIPART".equals(init.path("uploadMode").asText())) {
            throw new IllegalStateException("expected PRESIGNED_MULTIPART upload mode: " + init);
        }
        long partSize = init.path("partSize").asLong();
        if (partSize <= 0 || partSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("invalid presigned part size: " + partSize);
        }
        int totalParts = (int) ((content.length + partSize - 1) / partSize);
        if (init.path("parts").size() != totalParts) {
            throw new IllegalStateException("presigned part count does not match payload: " + init.path("parts"));
        }

        List<JsonNode> plans = new ArrayList<>();
        init.path("parts").forEach(plans::add);
        plans.sort(Comparator.comparingInt(part -> part.path("partNumber").asInt()));

        List<CompletedPart> completed = new ArrayList<>(plans.size());
        for (JsonNode plan : plans) {
            int partNumber = plan.path("partNumber").asInt();
            int offset = Math.toIntExact((partNumber - 1L) * partSize);
            int length = Math.min(Math.toIntExact(partSize), content.length - offset);
            byte[] part = new byte[length];
            System.arraycopy(content, offset, part, 0, length);
            completed.add(put(plan, part));
        }
        return completed;
    }

    static CompletedPart put(JsonNode plan, byte[] content) throws Exception {
        int partNumber = plan.path("partNumber").asInt();
        if (partNumber < 1) {
            throw new IllegalStateException("invalid presigned part plan: " + plan);
        }
        HttpResponse<Void> response = send(plan, content);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("presigned PUT failed with HTTP " + response.statusCode());
        }
        String etag = response.headers().firstValue("ETag")
                .orElseThrow(() -> new IllegalStateException("presigned PUT response has no ETag"));
        return new CompletedPart(partNumber, etag, content.length,
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
                response.headers().firstValue("Access-Control-Expose-Headers").orElse(""));
    }

    static int putStatus(JsonNode plan, byte[] content) throws Exception {
        return send(plan, content).statusCode();
    }

    static CorsPreflight preflight(JsonNode plan, String method) throws Exception {
        return preflight(plan, method, BROWSER_ORIGIN);
    }

    static CorsPreflight preflight(JsonNode plan, String method, String origin) throws Exception {
        String url = plan.path("url").asText();
        if (url.isBlank()) {
            throw new IllegalStateException("invalid presigned part plan: " + plan);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        return new CorsPreflight(
                response.statusCode(),
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
                response.headers().firstValue("Access-Control-Allow-Methods").orElse(""));
    }

    private static HttpResponse<Void> send(JsonNode plan, byte[] content) throws Exception {
        String url = plan.path("url").asText();
        if (url.isBlank()) {
            throw new IllegalStateException("invalid presigned part plan: " + plan);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Origin", BROWSER_ORIGIN)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    static List<Map<String, Object>> completionParts(List<CompletedPart> parts) {
        return parts.stream().map(part -> {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("partNumber", part.partNumber());
            request.put("etag", part.etag());
            return request;
        }).toList();
    }

    record CompletedPart(int partNumber, String etag, long size, String allowOrigin, String exposedHeaders) {

        CompletedPart(int partNumber, String etag, long size) {
            this(partNumber, etag, size, "", "");
        }
    }

    record CorsPreflight(int status, String allowOrigin, String allowMethods) {
    }
}
