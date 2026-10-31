package com.cidas.edudoc.common.smoke;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** 冒烟编排：查库 + 调 ai-service，聚合为单条结果。 */
@Service
public class SmokeService {

    private final DbProbe dbProbe;
    private final RestTemplate restTemplate;
    private final String aiServiceBaseUrl;

    public SmokeService(DbProbe dbProbe,
                        RestTemplate restTemplate,
                        @Value("${edudoc.ai-service.base-url}") String aiServiceBaseUrl) {
        this.dbProbe = dbProbe;
        this.restTemplate = restTemplate;
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }

    public SmokeResult smoke() {
        SmokeResult.DbStatus db = dbProbe.probe();
        @SuppressWarnings("unchecked")
        Map<String, Object> ai = restTemplate.getForObject(aiServiceBaseUrl + "/smoke", Map.class);
        return new SmokeResult("ok", db, ai);
    }
}
