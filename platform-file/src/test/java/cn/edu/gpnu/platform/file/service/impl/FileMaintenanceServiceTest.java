package cn.edu.gpnu.platform.file.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import io.minio.GetBucketLifecycleArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidResponseException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class FileMaintenanceServiceTest {

    private static final String BUCKET = "teacher-cert-test";
    private static final String SENSITIVE_MESSAGE = "simulated-sensitive-message";
    private static final String SENSITIVE_URL = "127.0.0.1:9000";
    private static final String SENSITIVE_BUCKET_PATH = "/" + BUCKET;
    private static final String SENSITIVE_TRACE = "simulated-sensitive-trace";
    private static final String FAILURE_CATEGORY_CLASS =
            FileMaintenanceService.class.getName() + "$LifecycleFailureCategory";

    @Mock
    private MinioClient minioClient;

    private FileMaintenanceService service;
    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        serviceLogger = (Logger) LoggerFactory.getLogger(FileMaintenanceService.class);
        logAppender = new ListAppender<>();
        logAppender.setContext(serviceLogger.getLoggerContext());
        logAppender.start();
        serviceLogger.addAppender(logAppender);

        MinioProperties properties = new MinioProperties();
        properties.setBucket(BUCKET);
        service = new FileMaintenanceService(minioClient, properties);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void noLifecycleConfigurationCreatesManagedRule() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class))).thenReturn(null);

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
        assertFailureLogged("ACCESS_DENIED", "AccessDenied");
    }

    @Test
    void serverErrorWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("InternalError", 500));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("SERVER_ERROR", "InternalError");
    }

    @Test
    void unknownServerErrorCodeUsesHttpStatusFallbackWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("UnexpectedServerFailure", 503));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("SERVER_ERROR", "UnexpectedServerFailure");
    }

    @Test
    void differentNotFoundCodeWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("NoSuchBucket", 404));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("NO_SUCH_BUCKET", "NoSuchBucket");
    }

    @Test
    void missingErrorResponseWhileReadingFailsClosedWithoutWriting() throws Exception {
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(null);
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class))).thenThrow(exception);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("INVALID_RESPONSE");
    }

    @Test
    void networkErrorWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(new IOException(SENSITIVE_MESSAGE));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("TRANSPORT");
    }

    @Test
    void parserErrorWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(new XmlParserException(new IllegalStateException(SENSITIVE_MESSAGE)));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("PARSE");
    }

    @Test
    void invalidResponseWhileReadingFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(new InvalidResponseException(
                        418, "text/plain", SENSITIVE_MESSAGE, SENSITIVE_TRACE));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("INVALID_RESPONSE");
    }

    @Test
    void unexpectedlyThrownNoLifecycleConfigurationFailsClosedWithoutWriting() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("NoSuchLifecycleConfiguration", 404));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("INVALID_RESPONSE", "NoSuchLifecycleConfiguration");
    }

    @Test
    void nullRulesWhileReadingFailsClosedWithoutWriting() throws Exception {
        LifecycleConfiguration invalidConfiguration = mock(LifecycleConfiguration.class);
        when(invalidConfiguration.rules()).thenReturn(null);
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenReturn(invalidConfiguration);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("INVALID_RESPONSE");
    }

    @Test
    void emptyRulesWhileReadingFailsClosedWithoutWriting() throws Exception {
        LifecycleConfiguration invalidConfiguration = mock(LifecycleConfiguration.class);
        when(invalidConfiguration.rules()).thenReturn(List.of());
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenReturn(invalidConfiguration);

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();

        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
        assertFailureLogged("INVALID_RESPONSE");
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

    @Test
    void logAssertionRejectsExtraRawSensitiveArgument() throws Exception {
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class)))
                .thenThrow(minioError("InternalError", 500));

        assertThat(service.ensureAbortIncompleteMultipartLifecycle(7)).isFalse();
        assertFailureLogged("SERVER_ERROR", "InternalError");
        Object productionCategory = logAppender.list.get(0).getArgumentArray()[0];
        logAppender.list.clear();

        serviceLogger.warn("MinIO 测试告警(category={})",
                productionCategory, SENSITIVE_MESSAGE);

        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage())
                            .contains("category=SERVER_ERROR")
                            .doesNotContain(SENSITIVE_MESSAGE);
                    assertThat(event.getArgumentArray())
                            .containsExactly(
                                    productionCategory, SENSITIVE_MESSAGE);
                });
        assertThatThrownBy(() -> assertFailureLogged("SERVER_ERROR"))
                .isInstanceOf(AssertionError.class);
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
                .url("http://" + SENSITIVE_URL + SENSITIVE_BUCKET_PATH)
                .get()
                .build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(SENSITIVE_MESSAGE)
                .build();
        return new ErrorResponseException(
                new ErrorResponse(
                        code,
                        SENSITIVE_MESSAGE,
                        BUCKET,
                        null,
                        SENSITIVE_BUCKET_PATH,
                        "request-id",
                        "host-id"),
                response,
                SENSITIVE_TRACE);
    }

    private void assertFailureLogged(String category, String... extraForbiddenValues) {
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> {
                    String message = event.getFormattedMessage();
                    assertThat(message).contains("category=" + category);
                    assertNoSensitiveLogValue(message, extraForbiddenValues);
                    assertThat(event.getThrowableProxy()).isNull();
                    assertThat(event.getArgumentArray())
                            .singleElement()
                            .satisfies(argument -> {
                                assertThat(argument).isNotInstanceOf(Throwable.class);
                                assertThat(argument).isInstanceOf(Enum.class);
                                assertThat(argument.getClass().getName())
                                        .isEqualTo(FAILURE_CATEGORY_CLASS);
                                assertThat(((Enum<?>) argument).name()).isEqualTo(category);
                                assertNoSensitiveLogValue(
                                        String.valueOf(argument), extraForbiddenValues);
                            });
                });
    }

    private static void assertNoSensitiveLogValue(
            String value, String... extraForbiddenValues) {
        assertThat(value).doesNotContain(
                SENSITIVE_MESSAGE,
                SENSITIVE_URL,
                SENSITIVE_BUCKET_PATH,
                SENSITIVE_TRACE);
        assertThat(extraForbiddenValues)
                .allSatisfy(forbidden -> assertThat(value).doesNotContain(forbidden));
    }

}
