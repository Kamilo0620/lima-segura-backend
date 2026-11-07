package com.limasegura.limasegurabackend.ingestion.connector;

import com.limasegura.limasegurabackend.model.NewsSource;
import java.util.List;

public interface SourceConnector {
    boolean supports(NewsSource source);
    List<DiscoveredItem> discover(NewsSource source);
}
