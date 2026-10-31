package com.cidas.edudoc.common.smoke;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** 冒烟编排实现：查库 + 调 ai-service，聚合为单条结果。 */
@Service
@RequiredArgsConstructor
public class SmokeServiceImpl implements SmokeService {

    private final DbProbe dbProbe;
    private final RestTemplate restTemplate;
    @Value("${edudoc.ai-service.base-url}")
    private final String aiServiceBaseUrl;

    @Override
    public SmokeResult smoke() {
        SmokeResult.DbStatus db = dbProbe.probe();
        @SuppressWarnings("unchecked")
        Map<String, Object> ai = restTemplate.getForObject(aiServiceBaseUrl + "/smoke", Map.class);
        return new SmokeResult("ok", db, ai);
    }
}
