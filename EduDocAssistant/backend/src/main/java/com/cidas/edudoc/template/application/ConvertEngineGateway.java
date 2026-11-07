package com.cidas.edudoc.template.application;

/** 转换引擎端口（J2 拍板：落 ai-service，HTTP 客户端实现只在 infrastructure）。 */
public interface ConvertEngineGateway {

    ConvertResult convert(byte[] sourceBytes, String fileName, String targetFormat);

    record ConvertResult(byte[] content, String contentType, String fileName) {
    }
}
