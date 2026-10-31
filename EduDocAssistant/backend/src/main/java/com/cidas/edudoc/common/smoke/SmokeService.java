package com.cidas.edudoc.common.smoke;

/** 冒烟编排：查库 + 调 ai-service，聚合为单条结果。 */
public interface SmokeService {

    SmokeResult smoke();
}
