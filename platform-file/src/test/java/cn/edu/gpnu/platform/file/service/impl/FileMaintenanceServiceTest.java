package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import io.minio.GetBucketLifecycleArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.XmlParserException;
import io.minio.messages.AbortIncompleteMultipartUpload;
import io.minio.messages.ErrorResponse;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMaintenanceServiceTest {

    private static final String BUCKET = "teacher-cert-test";

    @Mock
    private MinioClient minioClient;

    private FileMaintenanceService service;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket(BUCKET);
        service = new FileMaintenanceService(minioClient, properties);
    }

    @Test
    void noLifecycleConfigurationCreatesManagedRule() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("NoSuchLifecycleConfiguration", 404));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(11)).isTrue();

        ArgumentCaptor<SetBucketLifecycleArgs> captor =
                ArgumentCaptor.forClass(SetBucketLifecycleArgs.class);
        verify(minioClient).setBucketLifecycle(captor.capture());
        SetBucketLifecycleArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.config().rules())
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.id()).isEqualTo(FileMaintenanceService.ABORT_RULE_ID);
                    assertThat(rule.status()).isEqualTo(Status.ENABLED);
                    assertThat(rule.abortIncompleteMultipartUpload().daysAfterInitiation()).isEqualTo(11);
                    assertThat(rule.filter().prefix()).isEmpty();
                });
    }

    @Test
    void accessDeniedWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("AccessDenied", 403));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void serverErrorWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("InternalError", 500));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void differentNotFoundCodeWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("NoSuchBucket", 404));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void missingErrorResponseWhileReadingFailsClosedWithoutWriting() throws Exception {
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(null);
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class))).thenThrow(exception);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void networkErrorWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(new IOException("simulated read failure"));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void parserErrorWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(new XmlParserException(new IllegalStateException("simulated parser failure")));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void nullConfigurationWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class))).thenReturn(null);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void nullRulesWhileReadingFailsClosedWithoutWriting() throws Exception {
        LifecycleConfiguration invalidConfiguration = mock(LifecycleConfiguration.class);
        when(invalidConfiguration.rules()).thenReturn(null);
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenReturn(invalidConfiguration);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void emptyRulesWhileReadingFailsClosedWithoutWriting() throws Exception {
        LifecycleConfiguration invalidConfiguration = mock(LifecycleConfiguration.class);
        when(invalidConfiguration.rules()).thenReturn(List.of());
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenReturn(invalidConfiguration);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void updatePreservesUnrelatedRuleAndReplacesOnlyManagedRule() throws Exception {
        LifecycleRule unrelated = rule("operations-retention", 30);
        LifecycleRule staleManaged = rule(FileMaintenanceService.ABORT_RULE_ID, 3);
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenReturn(new LifecycleConfiguration(List.of(unrelated, staleManaged)));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(9)).isTrue();

        ArgumentCaptor<SetBucketLifecycleArgs> captor =
                ArgumentCaptor.forClass(SetBucketLifecycleArgs.class);
        verify(minioClient).setBucketLifecycle(captor.capture());
        List<LifecycleRule> rules = captor.getValue().config().rules();
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0)).isSameAs(unrelated);
        assertThat(rules).filteredOn(rule -> FileMaintenanceService.ABORT_RULE_ID.equals(rule.id()))
                .singleElement()
                .satisfies(rule ->
                        assertThat(rule.abortIncompleteMultipartUpload().daysAfterInitiation()).isEqualTo(9));
    }

    @Test
    void matchingManagedRuleSkipsWrite() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenReturn(new LifecycleConfiguration(
                        List.of(rule(FileMaintenanceService.ABORT_RULE_ID, 7))));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isTrue();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    private static LifecycleRule rule(String id, int abortDays) {
        return new LifecycleRule(
                Status.ENABLED,
                new AbortIncompleteMultipartUpload(abortDays),
                null,
                new RuleFilter(""),
                id,
                null,
                null,
                null);
    }

    private static ErrorResponseException minioError(String code, int status) {
        Request request = new Request.Builder()
                .url("http://127.0.0.1:9000/" + BUCKET)
                .get()
                .build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("simulated failure")
                .build();
        return new ErrorResponseException(
                new ErrorResponse(
                        code,
                        "simulated failure",
                        BUCKET,
                        null,
                        "/" + BUCKET,
                        "request-id",
                        "host-id"),
                response,
                "simulated trace");
    }
}
