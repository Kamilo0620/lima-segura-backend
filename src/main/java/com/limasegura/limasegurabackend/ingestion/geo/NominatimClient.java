package com.limasegura.limasegurabackend.ingestion.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NominatimClient {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Double[]> cache = new ConcurrentHashMap<>();

    public Double[] geocode(String address) {
        if (address == null || address.isBlank()) return null;
        
        String query = address + ", Peru";
        if (cache.containsKey(query)) {
            return cache.get(query);
        }

        try {
            Thread.sleep(1000); // 1 request/second rate limit
            String responseStr = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("nominatim.openstreetmap.org")
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .header(HttpHeaders.USER_AGENT, "LimaSeguraBot/1.0 (Academic Project)")
                    .retrieve()
                    .body(String.class);

            JsonNode arrayNode = objectMapper.readTree(responseStr);
            if (arrayNode.isArray() && arrayNode.size() > 0) {
                JsonNode first = arrayNode.get(0);
                Double lat = first.get("lat").asDouble();
                Double lon = first.get("lon").asDouble();
                Double[] coords = new Double[]{lat, lon};
                cache.put(query, coords);
                return coords;
            }
        } catch (Exception e) {
            log.error("Failed to geocode address: {}", address, e);
        }
        return null;
    }
}
