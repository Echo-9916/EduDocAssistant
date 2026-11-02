package com.cidas.edudoc.infrastructure.oss;

import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.UploadCredentials;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OssStorageAdapterTest {

    private final com.aliyun.sts20150401.Client stsClient = mock(com.aliyun.sts20150401.Client.class);
    private final OssProperties properties = new OssProperties("https://oss-cn-beijing.aliyuncs.com",
            "cn-beijing", "cidas-oss-bucket", "ak", "sk", "acs:ram::1:role/edudoc-upload",
            "sts.cn-beijing.aliyuncs.com", "edudoc/uploads", 120, 900);
    private final OssStorageAdapter adapter = new OssStorageAdapter(properties, stsClient);

    private static AssumeRoleResponse stubResponse() {
        AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                new AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials()
                        .setAccessKeyId("STS.AK").setAccessKeySecret("STS.SK")
                        .setSecurityToken("TOKEN").setExpiration("2026-11-02T12:00:00Z");
        return new AssumeRoleResponse().setBody(new AssumeRoleResponseBody().setCredentials(credentials));
    }

    @Test
    void assumeRolePolicyScopedToSingleObjectKey() throws Exception {
        ArgumentCaptor<AssumeRoleRequest> captor = ArgumentCaptor.forClass(AssumeRoleRequest.class);
        when(stsClient.assumeRole(captor.capture())).thenReturn(stubResponse());

        UploadCredentials creds = adapter.issueUploadCredentials(
                "edudoc/uploads/202611/U1/s-lecture.pdf", "edudoc-s");

        assertEquals("STS.AK", creds.accessKeyId());
        assertEquals("cidas-oss-bucket", creds.bucket());
        AssumeRoleRequest request = captor.getValue();
        assertEquals("acs:ram::1:role/edudoc-upload", request.getRoleArn());
        assertEquals(900L, request.getDurationSeconds());
        String policy = request.getPolicy();
        assertTrue(policy.contains("acs:oss:*:*:cidas-oss-bucket/edudoc/uploads/202611/U1/s-lecture.pdf"),
                "inline policy 必须精确到单个 objectKey");
        assertTrue(policy.contains("oss:PutObject") && policy.contains("oss:ListParts"));
    }

    @Test
    void stsFailureMapsToServiceUnavailable() throws Exception {
        when(stsClient.assumeRole(any())).thenThrow(new RuntimeException("NoPermission"));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> adapter.issueUploadCredentials("k", "s"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.errorCode());
        assertTrue(e.retryable());
    }
}
