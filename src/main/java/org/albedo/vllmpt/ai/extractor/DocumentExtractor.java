package org.albedo.vllmpt.ai.extractor;


import org.springframework.stereotype.Component;

import java.io.IOException;

public interface DocumentExtractor {
    String extractText(String urlOrPath) throws IOException;
}

