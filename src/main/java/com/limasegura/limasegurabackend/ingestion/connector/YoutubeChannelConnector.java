package com.limasegura.limasegurabackend.ingestion.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.ingestion.filter.TitleRelevanceFilter;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class YoutubeChannelConnector implements SourceConnector {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final ObjectMapper objectMapper = new ObjectMapper();
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
                log.debug("XML feed unavailable for YouTube source {}, switching to ytInitialData discovery", source.getName());
            }
        }

        // 2. Si el feed XML devolvió 404 o 0 videos policiales recientes, consultar ytInitialData filtrado a esta semana
        if (items.isEmpty()) {
            items.addAll(discoverFromYouTubeInitialData(source));
        }

        return items;
    }

    private List<DiscoveredItem> discoverFromYouTubeInitialData(NewsSource source) {
        List<DiscoveredItem> found = new ArrayList<>();
        Set<String> seenVideoIds = new LinkedHashSet<>();

        try {
            String channelName = source.getName() != null
                    ? source.getName().replaceAll("\\(.*?\\)", "").trim()
                    : "Noticias Peru";
            String query = channelName + " policiales Lima Callao";
            String searchUrl = "https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            String html = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "es-PE,es;q=0.9")
                    .cookie("CONSENT", "YES+cb.20210328-17-p0.es+FX+999")
                    .timeout(12000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            int idx = html.indexOf("ytInitialData");
            if (idx != -1) {
                int start = html.indexOf('{', idx);
                int end = html.indexOf(";</script>", start);
                if (start != -1 && end != -1 && end > start) {
                    JsonNode root = objectMapper.readTree(html.substring(start, end));
                    collectVideoRenderers(root, found, seenVideoIds);
                }
            }
            log.info("YouTube ytInitialData discovery for '{}': found {} recent crime videos", source.getName(), found.size());
        } catch (Exception e) {
            log.warn("Failed to discover items from YouTube source {} ({}): {}", source.getId(), source.getName(), e.getMessage());
        }

        return found;
    }

    private void collectVideoRenderers(JsonNode node, List<DiscoveredItem> found, Set<String> seenVideoIds) {
        if (node == null || found.size() >= 12) {
            return;
        }
        if (node.isObject()) {
            JsonNode vr = node.get("videoRenderer");
            if (vr != null && vr.hasNonNull("videoId")) {
                String videoId = vr.get("videoId").asText();
                if (!seenVideoIds.contains(videoId)) {
                    String title = extractRunsText(vr.get("title"));
                    String publishedTimeText = extractRunsText(vr.get("publishedTimeText"));
                    LocalDateTime publishedAt = parseRecentYouTubeTime(publishedTimeText);

                    // Solo aceptar videos recientes (publishedAt != null indica <= 7 días) y con título delictivo válido
                    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                    if (publishedAt != null && titleRelevanceFilter.isRelevantCrimeTitle(title, videoUrl)) {
                        String snippet = extractSnippetText(vr);
                        seenVideoIds.add(videoId);
                        String content = "TITULAR DEL VIDEO: " + title
                                + "\nANTIGÜEDAD REPORTADA EN YOUTUBE: " + publishedTimeText
                                + (snippet.isBlank() ? "" : "\nDESCRIPCIÓN: " + snippet);
                        found.add(DiscoveredItem.builder()
                                .externalId(videoId)
                                .url(videoUrl)
                                .title(title)
                                .contentText(content)
                                .publishedAt(publishedAt)
                                .build());
                    }
                }
            }
            node.fields().forEachRemaining(entry -> collectVideoRenderers(entry.getValue(), found, seenVideoIds));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectVideoRenderers(child, found, seenVideoIds);
            }
        }
    }

    /**
     * Convierte "hace 3 horas", "hace 45 minutos", "hace 2 días" en LocalDateTime real.
     * Devuelve null si el video es antiguo (semanas, meses o años), descartándolo de inmediato.
     */
    private LocalDateTime parseRecentYouTubeTime(String publishedTimeText) {
        if (publishedTimeText == null || publishedTimeText.isBlank()) {
            return LocalDateTime.now().minusHours(4);
        }
        String lower = publishedTimeText.toLowerCase(Locale.ROOT);
        // Descartar videos antiguos (años o meses)
        if (lower.contains("año") || lower.contains("year") || lower.contains("mes") || lower.contains("month")) {
            return null;
        }

        int value = 1;
        Matcher m = NUMBER_PATTERN.matcher(lower);
        if (m.find()) {
            try {
                value = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }

        LocalDateTime now = LocalDateTime.now();
        if (lower.contains("minuto") || lower.contains("minute") || lower.contains("seg") || lower.contains("sec")) {
            return now.minusMinutes(Math.min(value, 180));
        }
        if (lower.contains("hora") || lower.contains("hour")) {
            return now.minusHours(Math.min(value, 72));
        }
        if (lower.contains("día") || lower.contains("dia") || lower.contains("day")) {
            return now.minusDays(Math.min(value, 14)).minusHours((value * 3L) % 12);
        }
        if (lower.contains("semana") || lower.contains("week")) {
            if (value > 2) return null;
            return now.minusDays(value * 5L).minusHours(6);
        }
        return now.minusHours(5);
    }

    private String extractRunsText(JsonNode titleNode) {
        if (titleNode == null) return "";
        if (titleNode.hasNonNull("simpleText")) {
            return titleNode.get("simpleText").asText();
        }
        JsonNode runs = titleNode.get("runs");
        if (runs != null && runs.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode run : runs) {
                if (run.hasNonNull("text")) {
                    sb.append(run.get("text").asText());
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private String extractSnippetText(JsonNode vr) {
        JsonNode detailed = vr.get("detailedMetadataSnippets");
        if (detailed != null && detailed.isArray() && !detailed.isEmpty()) {
            String text = extractRunsText(detailed.get(0).get("snippetText"));
            if (!text.isBlank()) return text;
        }
        return extractRunsText(vr.get("descriptionSnippet"));
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
