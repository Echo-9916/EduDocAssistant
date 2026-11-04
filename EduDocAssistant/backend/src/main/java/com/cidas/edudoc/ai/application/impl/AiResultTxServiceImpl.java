package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.AiResultTxService;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiResultTxServiceImpl implements AiResultTxService {

    private final AiTaskRepository aiTaskRepository;
    private final AiResultRepository aiResultRepository;

    @Override
    @Transactional
    public boolean completeTask(AiResult result, List<AiCitation> citations) {
        // 守卫先行：RUNNING→SUCCEEDED 迁移失败即取消竞争落败，结果与引用一律不写
        if (!aiTaskRepository.markSucceeded(result.getTaskId())) {
            return false;
        }
        aiResultRepository.insert(result);
        aiResultRepository.insertCitations(citations);
        return true;
    }
}
