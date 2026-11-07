package com.limasegura.limasegurabackend.ingestion.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstitutionalConnector implements SourceConnector {

    private final ObjectMapper objectMapper;
    private final RssNewsConnector rssNewsConnector;

    @Override
    public boolean supports(NewsSource source) {
        return source.getType() == SourceType.INSTITUTIONAL_API;
    }

    @Override
    public List<DiscoveredItem> discover(NewsSource source) {
        List<DiscoveredItem> items = new ArrayList<>();
        
        // If the URL ends with .xml or /rss or contains rss/feed, try RSS first
        if (source.getUrl().contains("rss") || source.getUrl().contains("feed") || source.getUrl().endsWith(".xml")) {
            return rssNewsConnector.discover(source);
        }

        try {
            URL url = new URL(source.getUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "LimaSeguraBot/1.0 (Emergency Ingestion Engine)");
            conn.setRequestProperty("Accept", "application/json, text/html, */*");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("Institutional source {} returned HTTP status {}", source.getName(), status);
                return items;
            }

            String contentType = conn.getContentType();
            if (contentType != null && contentType.contains("xml")) {
                return rssNewsConnector.discover(source);
            }

            try (InputStream in = conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        DiscoveredItem item = parseJsonNode(node, source);
                        if (item != null) items.add(item);
                    }
                } else if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode node : root.get("data")) {
                        DiscoveredItem item = parseJsonNode(node, source);
                        if (item != null) items.add(item);
                    }
                } else if (root.has("emergencias") && root.get("emergencias").isArray()) { // Bomberos Dimapad format
                    for (JsonNode node : root.get("emergencias")) {
                        DiscoveredItem item = parseJsonNode(node, source);
                        if (item != null) items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error discovering items from institutional source {}: {}", source.getName(), e.getMessage());
        }

        return items;
    }

    private DiscoveredItem parseJsonNode(JsonNode node, NewsSource source) {
        try {
            String id = node.has("id") ? node.get("id").asText() :
                    (node.has("codigo") ? node.get("codigo").asText() : String.valueOf(node.hashCode()));
            
            String title = node.has("titulo") ? node.get("titulo").asText() :
                    (node.has("tipo") ? node.get("tipo").asText() :
                            (node.has("descripcion") ? node.get("descripcion").asText() : "Alerta Institucional"));

            String link = node.has("url") ? node.get("url").asText() :
                    (node.has("link") ? node.get("link").asText() : source.getUrl() + "#" + id);

            LocalDateTime pubDate = LocalDateTime.now();
            if (node.has("fecha")) {
                try {
                    pubDate = LocalDateTime.parse(node.get("fecha").asText(), DateTimeFormatter.ISO_DATE_TIME);
                } catch (Exception ignored) {}
            }

            return DiscoveredItem.builder()
                    .externalId(id)
                    .url(link)
                    .title(title)
                    .publishedAt(pubDate)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
