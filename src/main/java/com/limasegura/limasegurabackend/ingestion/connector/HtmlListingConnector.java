package com.limasegura.limasegurabackend.ingestion.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtmlListingConnector implements SourceConnector {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(NewsSource source) {
        return source.getType() == SourceType.NEWSPAPER_HTML;
    }

    @Override
    public List<DiscoveredItem> discover(NewsSource source) {
        List<DiscoveredItem> items = new ArrayList<>();
        try {
            String articleSelector = "a.news-item";
            String titleSelector = null;
            String baseUrl = "";

            if (source.getConfig() != null && !source.getConfig().isBlank()) {
                JsonNode configNode = objectMapper.readTree(source.getConfig());
                if (configNode.has("articleSelector")) {
                    articleSelector = configNode.get("articleSelector").asText();
                }
                if (configNode.has("titleSelector")) {
                    titleSelector = configNode.get("titleSelector").asText();
                }
                if (configNode.has("baseUrl")) {
                    baseUrl = configNode.get("baseUrl").asText();
                }
            }

            Document doc = Jsoup.connect(source.getUrl())
                    .userAgent("LimaSeguraBot/1.0")
                    .timeout(15000)
                    .get();

            Elements articleElements = doc.select(articleSelector);
            for (Element element : articleElements) {
                String href = element.attr("href");
                if (href.isBlank()) continue;

                String absoluteUrl = href.startsWith("http") ? href : baseUrl + href;
                String title = titleSelector != null ? element.select(titleSelector).text() : element.text();

                items.add(DiscoveredItem.builder()
                        .externalId(absoluteUrl)
                        .url(absoluteUrl)
                        .title(title)
                        .build());
            }

        } catch (Exception e) {
            log.warn("Failed to discover items from HTML source {}: {}", source.getId(), e.getMessage());
        }
        return items;
    }
}
