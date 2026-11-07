package com.limasegura.limasegurabackend.ingestion.connector;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RssNewsConnector implements SourceConnector {

    @Override
    public boolean supports(NewsSource source) {
        return source.getType() == SourceType.NEWSPAPER_RSS;
    }

    @Override
    public List<DiscoveredItem> discover(NewsSource source) {
        List<DiscoveredItem> items = new ArrayList<>();
        try {
            URL url = new URL(source.getUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (InputStream in = conn.getInputStream()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(in);

                NodeList itemNodes = doc.getElementsByTagName("item");
                if (itemNodes.getLength() == 0) {
                    itemNodes = doc.getElementsByTagName("entry"); // Fallback for Atom
                }

                for (int i = 0; i < itemNodes.getLength(); i++) {
                    Element element = (Element) itemNodes.item(i);
                    String link = getTagValue(element, "link");
                    if (link == null || link.isBlank()) {
                        // Atom links
                        NodeList links = element.getElementsByTagName("link");
                        if (links.getLength() > 0) {
                            link = ((Element) links.item(0)).getAttribute("href");
                        }
                    }
                    
                    String title = getTagValue(element, "title");
                    String pubDateStr = getTagValue(element, "pubDate");
                    if (pubDateStr == null) pubDateStr = getTagValue(element, "published");
                    
                    String guid = getTagValue(element, "guid");
                    if (guid == null) guid = getTagValue(element, "id");
                    if (guid == null) guid = link;

                    String description = getTagValue(element, "description");
                    if (description == null) description = getTagValue(element, "summary");
                    if (description == null) description = getTagValue(element, "content:encoded");
                    if (description != null) {
                        description = description.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                    }

                    LocalDateTime pubDate = null;
                    if (pubDateStr != null) {
                        try {
                            pubDate = LocalDateTime.parse(pubDateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
                        } catch (DateTimeParseException e) {
                            try {
                                pubDate = LocalDateTime.parse(pubDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                            } catch (DateTimeParseException ex) {
                                log.debug("Failed to parse date: {}", pubDateStr);
                            }
                        }
                    }

                    if (link != null && title != null) {
                        items.add(DiscoveredItem.builder()
                                .externalId(guid)
                                .url(link)
                                .title(title)
                                .contentText(description)
                                .publishedAt(pubDate)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to discover items from RSS source {}: {}", source.getId(), e.getMessage());
        }
        return items;
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}
