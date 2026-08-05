package org.wigout.mcp.common.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only view of the generated Bitwig API docs index
 * (bitwig-api-index.json, produced by scripts/generate-api-index.ps1).
 * Supplies javadoc summaries, @since versions, and deprecation replacement
 * hints for bw_describe and for deprecation-refusal error messages.
 */
public final class ApiDocIndex {

    /** One documented method. */
    public record MethodDoc(String name, String signature, String doc, Integer since,
                            boolean deprecated, String replacement) {}

    private record TypeDoc(String kind, java.util.List<String> extendsTypes, String doc,
                           Map<String, MethodDoc> methodsByName) {}

    private static final String RESOURCE = "/bitwig-api-index.json";
    private static volatile ApiDocIndex instance;

    private final Map<String, TypeDoc> types;
    private final String rawJson;

    private ApiDocIndex(Map<String, TypeDoc> types, String rawJson) {
        this.types = types;
        this.rawJson = rawJson;
    }

    /** Loads (once) from the classpath resource. */
    public static ApiDocIndex load() {
        ApiDocIndex local = instance;
        if (local == null) {
            synchronized (ApiDocIndex.class) {
                if (instance == null) {
                    instance = parse();
                }
                local = instance;
            }
        }
        return local;
    }

    private static ApiDocIndex parse() {
        try (InputStream in = ApiDocIndex.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = new ObjectMapper().readTree(json).path("types");
            Map<String, TypeDoc> types = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode t = entry.getValue();
                java.util.List<String> ext = new java.util.ArrayList<>();
                t.path("extends").forEach(e -> ext.add(e.asText()));
                Map<String, MethodDoc> methods = new LinkedHashMap<>();
                t.path("methods").forEach(m -> methods.putIfAbsent(m.path("name").asText(), new MethodDoc(
                    m.path("name").asText(),
                    m.path("signature").asText(),
                    m.path("doc").asText(""),
                    m.path("since").isNumber() ? m.path("since").asInt() : null,
                    m.path("deprecated").asBoolean(false),
                    m.path("replacement").isTextual() ? m.path("replacement").asText() : null)));
                types.put(entry.getKey(), new TypeDoc(t.path("kind").asText(), ext, t.path("doc").asText(""), methods));
            });
            return new ApiDocIndex(types, json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + RESOURCE + ": " + e.getMessage(), e);
        }
    }

    /** Looks up a method on the type or (breadth-first) its extends chain. */
    public Optional<MethodDoc> forMethod(String typeSimpleName, String methodName) {
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(typeSimpleName);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!seen.add(name)) {
                continue;
            }
            TypeDoc type = types.get(name);
            if (type == null) {
                continue;
            }
            MethodDoc doc = type.methodsByName().get(methodName);
            if (doc != null) {
                return Optional.of(doc);
            }
            queue.addAll(type.extendsTypes());
        }
        return Optional.empty();
    }

    public Optional<String> typeDoc(String typeSimpleName) {
        TypeDoc type = types.get(typeSimpleName);
        return type == null ? Optional.empty() : Optional.of(type.doc());
    }

    public Set<String> typeNames() {
        return Collections.unmodifiableSet(types.keySet());
    }

    /** The raw generated JSON (served by the API-map MCP resource). */
    public String rawJson() {
        return rawJson;
    }
}
