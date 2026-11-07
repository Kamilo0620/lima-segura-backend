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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class YoutubeChannelConnector implements SourceConnector {

    @Override
    public boolean supports(NewsSource source) {
        return source.getType() == SourceType.YOUTUBE_CHANNEL;
    }

    @Override
    public List<DiscoveredItem> discover(NewsSource source) {
        List<DiscoveredItem> items = new ArrayList<>();
        try {
            String feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + source.getChannelId();
            URL url = new URL(feedUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "LimaSeguraBot/1.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

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
                    String publishedStr = getTagValue(entry, "published", null);
                    
                    if (videoId == null) continue;

                    LocalDateTime pubDate = null;
                    if (publishedStr != null) {
                        try {
                            pubDate = LocalDateTime.parse(publishedStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        } catch (Exception e) {
                            log.debug("Failed to parse Youtube date: {}", publishedStr);
                        }
                    }

                    items.add(DiscoveredItem.builder()
                            .externalId(videoId)
                            .url("https://www.youtube.com/watch?v=" + videoId)
                            .title(title)
                            .publishedAt(pubDate)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to discover items from YouTube source {}: {}", source.getId(), e.getMessage());
        }
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
