package com.cidas.edudoc.document.application;

/** 文档图片：块编辑中上传、预览时读取（私有 OSS 代理，不暴露长期凭据）。 */
public interface DocumentImageService {

    ImageView upload(String documentId, Upload upload);

    /** 已登录可读用户（owner 或协作者）读取图片。 */
    ImageContent load(String documentId, String imageId);

    /** 免登录共享链接读取图片（token 校验并限定到该文档）。 */
    ImageContent loadPublic(String token, String imageId);

    record Upload(String fileName, String mimeType, long sizeBytes, byte[] bytes) {
    }

    record ImageView(String id, String fileName, String mimeType, long sizeBytes) {
    }

    record ImageContent(String fileName, String mimeType, byte[] bytes) {
    }
}