package com.limasegura.limasegurabackend.ingestion.connector;

import com.limasegura.limasegurabackend.ingestion.filter.TitleRelevanceFilter;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class YoutubeChannelConnector implements SourceConnector {

    private final TitleRelevanceFilter titleRelevanceFilter = new TitleRelevanceFilter();

    @Override
    public boolean supports(NewsSource source) {
        return source.getType() == SourceType.YOUTUBE_CHANNEL;
    }

    @Override
    public List<DiscoveredItem> discover(NewsSource source) {
        List<DiscoveredItem> items = new ArrayList<>();

        // 1. Intentar primero el feed Atom XML si YouTube lo habilita
        if (source.getChannelId() != null && !source.getChannelId().isBlank()) {
            try {
                String feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + source.getChannelId();
                URL url = new URL(feedUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(in);

                        NodeList entryNodes = doc.getElementsByTagNameNS("*", "entry");
                        for (int i = 0; i < entryNodes.getLength(); i++) {
                            Element entry = (Element) entryNodes.item(i);
                            String videoId = getTagValue(entry, "videoId", "http://www.youtube.com/xml/schemas/2015");
                            String title = getTagValue(entry, "title", null);
                            String description = getTagValue(entry, "description", "http://search.yahoo.com/mrss/");
                            String publishedStr = getTagValue(entry, "published", null);

                            if (videoId == null || !titleRelevanceFilter.isRelevantCrimeTitle(title, "")) {
                                continue;
                            }

                            LocalDateTime pubDate = LocalDateTime.now();
                            if (publishedStr != null) {
                                try {
                                    pubDate = LocalDateTime.parse(publishedStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                                } catch (Exception ignored) {}
                            }

                            // Ignorar videos de más de 7 días de antigüedad
                            if (Duration.between(pubDate, LocalDateTime.now()).toDays() > 7) {
                                continue;
                            }

                            items.add(DiscoveredItem.builder()
                                    .externalId(videoId)
                                    .url("https://www.youtube.com/watch?v=" + videoId)
                                    .title(title)
                                    .contentText(description != null && !description.isBlank() ? title + "\n\n" + description : title)
                                    .publishedAt(pubDate)
                                    .build());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("YouTube RSS feed unavailable for source {}: {}", source.getName(), e.getMessage());
            }
        }

        // Solo usamos el feed RSS oficial del canal. Leer la página de búsqueda de YouTube
        // (ytInitialData) se rompe con cualquier cambio de la web y va contra sus términos de uso.
        return items;
    }

    private String getTagValue(Element element, String localName, String namespaceUri) {
        NodeList nodeList;
        if (namespaceUri != null) {
            nodeList = element.getElementsByTagNameNS(namespaceUri, localName);
        } else {
            nodeList = element.getElementsByTagName(localName);
        }

        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}
