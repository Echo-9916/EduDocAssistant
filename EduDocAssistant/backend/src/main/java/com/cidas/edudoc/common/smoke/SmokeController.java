package com.cidas.edudoc.common.smoke;

import com.cidas.edudoc.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 冒烟探针：验证后端 -> Oracle 与后端 -> ai-service 两条链路。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SmokeController {

    private final SmokeService smokeService;

    @GetMapping("/smoke")
    public ApiResponse<SmokeResult> smoke() {
        return ApiResponse.ok(smokeService.smoke());
    }
}
