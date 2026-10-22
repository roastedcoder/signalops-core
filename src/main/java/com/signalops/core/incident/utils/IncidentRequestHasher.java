package com.signalops.core.incident.utils;

import com.signalops.core.incident.controller.IncidentController;
import com.signalops.core.incident.dto.CreateIncidentRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Component
public class IncidentRequestHasher {
    private final ObjectMapper objectMapper;

    public IncidentRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(CreateIncidentRequest request) {
        try {
            // Sort metadata keys so their ordering doesn't affect the hash.
            Map<String, Object> canonicalMetadata =
                    request.metadata() == null
                            ? Map.of()
                            : new TreeMap<>(request.metadata());

            Map<String, Object> canonicalRequest = new TreeMap<>();
            canonicalRequest.put("title", request.title());
            canonicalRequest.put("description", request.description());
            canonicalRequest.put("severity", request.severity());
            canonicalRequest.put("source", request.source());
            canonicalRequest.put("metadata", canonicalMetadata);

            byte[] payload = objectMapper.writeValueAsBytes(canonicalRequest);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload);

            return HexFormat.of().formatHex(digest);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize incident request", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
