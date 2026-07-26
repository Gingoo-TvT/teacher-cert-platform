package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.business.video.support.VideoMediaInspection;
import cn.edu.gpnu.platform.business.video.support.VideoMediaProbe;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.system.service.ParamService;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoDataInitializerTest {

    private static final String BUCKET = "teacher-cert-demo-unit";
    private static final String VIDEO_SHA256 =
            "0a15c2e382438f3fbbd1e9b5302514ad35da07b33b44f7eb61fbb5c8b31d611d";
    private static final String MATERIAL_SHA256 =
            "53aece50aefaf8236338f0ddada8469e54d1422d26da0a975a2f81c873ba9268";
    private static final String IMAGE_SHA256 =
            "c589a04293ea064c50a46e8fec832e6920b28ea3cf3718aed18b929f9fb631ee";
    private static final String VIDEO_KEY = "teaching-video/demo/" + VIDEO_SHA256 + ".mp4";
    private static final String MATERIAL_KEY = "process-material/demo/" + MATERIAL_SHA256 + ".pdf";
    private static final String IMAGE_KEY = "process-material/demo/" + IMAGE_SHA256 + ".png";
    private static final String EXEMPTION_KEY =
            "exemption-material/demo/" + MATERIAL_SHA256 + ".pdf";
    private static final String LEGACY_VIDEO_KEY = "teaching-video/demo-teaching-video.mp4";
    private static final String LEGACY_MATERIAL_KEY = "process-material/demo-material.pdf";
    private static final String LEGACY_IMAGE_KEY = "process-material/demo-material-image.png";
    private static final String LEGACY_EXEMPTION_KEY = "exemption-material/demo-exemption.pdf";
    private static final String VIDEO_RESOURCE = "db/demo/sample-video.mp4";
    private static final String MATERIAL_RESOURCE = "db/demo/sample-material.pdf";
    private static final String IMAGE_RESOURCE = "db/demo/sample-image.png";
    private static final long VIDEO_SIZE = 1_605_702L;
    private static final String VIDEO_FINGERPRINT =
            "ee0f34bb9c2cdaa567cb1195b957f28d56c6279f972b9b3afe1c4b235ab21f86";
    private static final String POLICY_HASH =
            "450225d274f6959ed2fe8fb299e0cbb91f508604a2aece234524e8246c82ddc9";
    private static final String PROBE_VERSION = "JCODEC_PROCESS_V4";
    private static final String MATERIAL_MD5 = "04ef7b13998c0fecd52bbf07af84bd05";
    private static final String IMAGE_MD5 = "2fba59bf7077925f03e1d9431b792bed";

    @Mock
    private DataSource dataSource;
    @Mock
    private MinioClient minioClient;
    @Mock
    private VideoMediaProbe videoMediaProbe;
    @Mock
    private ParamService paramService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private DemoDataInitializer initializer;
    private InMemoryObjectStore objectStore;

    @BeforeEach
    void setUp() throws Exception {
        MinioProperties properties = new MinioProperties();
        properties.setBucket(BUCKET);
        initializer = new DemoDataInitializer(
                dataSource,
                minioClient,
                properties,
                videoMediaProbe,
                paramService,
                transactionManager);
        objectStore = new InMemoryObjectStore(minioClient);
        objectStore.install();

        when(videoMediaProbe.currentPolicyHash()).thenReturn(POLICY_HASH);
        when(videoMediaProbe.probeVersion()).thenReturn(PROBE_VERSION);
        when(videoMediaProbe.inspect(anyString(), anyLong(), anyString()))
                .thenReturn(trustedInspection());
        when(paramService.getInt(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(paramService.getString(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void exactObjectsAreSkippedAndTrustedManifestIsSeeded() throws Exception {
        loadExactObjects();
        DemoDataInitializer runner = runnerSpy();

        runner.run(null);

        ArgumentCaptor<DemoDataInitializer.ReconcileSummary> summaryCaptor =
                ArgumentCaptor.forClass(DemoDataInitializer.ReconcileSummary.class);
        verify(runner).seedEntities(summaryCaptor.capture());
        DemoDataInitializer.ReconcileSummary summary = summaryCaptor.getValue();
        assertThat(summary.created()).isZero();
        assertThat(summary.unchanged()).isEqualTo(4);
        assertTrustedVideoManifest(summary.videoManifest());
        assertThat(objectStore.putKeys()).isEmpty();
        verify(videoMediaProbe).inspect(VIDEO_KEY, VIDEO_SIZE, VIDEO_FINGERPRINT);
    }

    @Test
    void missingObjectsAreUploadedReadBackAndCountedAsCreated() throws Exception {
        DemoDataInitializer.ReconcileSummary summary = initializer.reconcileSampleObjects(BUCKET);

        assertThat(summary.created()).isEqualTo(4);
        assertThat(summary.unchanged()).isZero();
        assertThat(objectStore.putKeys())
                .containsExactly(VIDEO_KEY, MATERIAL_KEY, IMAGE_KEY, EXEMPTION_KEY);
        assertExactStoredObjects();
        assertTrustedVideoManifest(summary.videoManifest());
    }

    @Test
    void legacy528ByteFixedKeyDoesNotBlockVersionedUpgrade() throws Exception {
        loadExactNonVideoObjects();
        objectStore.putDirect(LEGACY_VIDEO_KEY, new byte[528], "video/mp4");

        DemoDataInitializer.ReconcileSummary summary = initializer.reconcileSampleObjects(BUCKET);

        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.unchanged()).isEqualTo(3);
        assertThat(objectStore.putKeys()).containsExactly(VIDEO_KEY);
        assertThat(objectStore.get(VIDEO_KEY).bytes()).isEqualTo(resourceBytes(VIDEO_RESOURCE));
        assertThat(objectStore.get(LEGACY_VIDEO_KEY).bytes()).hasSize(528);
        assertTrustedVideoManifest(summary.videoManifest());
    }

    @Test
    void sqlFailureAfterVersionUploadLeavesLegacyObjectUntouched() throws Exception {
        loadExactNonVideoObjects();
        byte[] legacyBytes = new byte[528];
        objectStore.putDirect(LEGACY_VIDEO_KEY, legacyBytes, "video/mp4");
        DemoDataInitializer runner = spy(initializer);
        doThrow(new IllegalStateException("simulated SQL failure"))
                .when(runner)
                .seedEntities(any(DemoDataInitializer.ReconcileSummary.class));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("演示数据初始化失败，已终止启动")
                .hasNoCause();

        assertThat(objectStore.get(LEGACY_VIDEO_KEY).bytes()).isEqualTo(legacyBytes);
        assertThat(objectStore.get(VIDEO_KEY).bytes()).isEqualTo(resourceBytes(VIDEO_RESOURCE));
    }

    @Test
    void sameLengthWrongContentAtVersionedKeyFailsClosedWithoutOverwrite() throws Exception {
        loadExactObjects();
        byte[] wrongBytes = resourceBytes(VIDEO_RESOURCE);
        wrongBytes[wrongBytes.length - 1] ^= 0x01;
        objectStore.putDirect(VIDEO_KEY, wrongBytes, "video/mp4");

        assertThatThrownBy(() -> initializer.reconcileSampleObjects(BUCKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("演示内容版本对象与已审核资源不一致");

        assertThat(objectStore.putKeys()).isEmpty();
        assertThat(objectStore.get(VIDEO_KEY).bytes()).isEqualTo(wrongBytes);
    }

    @Test
    void wrongContentTypeAtVersionedKeyFailsClosedWithoutOverwrite() throws Exception {
        loadExactObjects();
        objectStore.putDirect(VIDEO_KEY, resourceBytes(VIDEO_RESOURCE), "application/octet-stream");

        assertThatThrownBy(() -> initializer.reconcileSampleObjects(BUCKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("演示内容版本对象与已审核资源不一致");

        assertThat(objectStore.putKeys()).isEmpty();
        assertThat(objectStore.get(VIDEO_KEY).contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void statFailureStopsStartupBeforeEntitySeed() throws Exception {
        loadExactObjects();
        objectStore.statFailure(minioError("AccessDenied"));

        assertRunnerFailsClosedWithoutSeed();

        assertThat(objectStore.putKeys()).isEmpty();
    }

    @Test
    void getFailureStopsStartupBeforeEntitySeed() throws Exception {
        loadExactObjects();
        objectStore.getFailure(new IOException("simulated read failure"));

        assertRunnerFailsClosedWithoutSeed();

        assertThat(objectStore.putKeys()).isEmpty();
    }

    @Test
    void putFailureStopsStartupBeforeEntitySeed() throws Exception {
        objectStore.putFailure(new IOException("simulated write failure"));

        assertRunnerFailsClosedWithoutSeed();

        assertThat(objectStore.putKeys()).isEmpty();
    }

    @Test
    void postWriteContentMismatchStopsStartupBeforeEntitySeed() throws Exception {
        objectStore.corruptWrites();

        assertRunnerFailsClosedWithoutSeed();

        assertThat(objectStore.putKeys()).containsExactly(VIDEO_KEY);
    }

    @Test
    void probeFailureStopsStartupBeforeEntitySeed() throws Exception {
        loadExactObjects();
        when(videoMediaProbe.inspect(anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("simulated probe failure"));

        assertRunnerFailsClosedWithoutSeed();
    }

    @Test
    void technicallyInvalidVideoStopsStartupBeforeEntitySeed() throws Exception {
        loadExactObjects();
        when(videoMediaProbe.inspect(anyString(), anyLong(), anyString()))
                .thenReturn(VideoMediaInspection.invalid(
                        "invalid media",
                        VIDEO_FINGERPRINT,
                        900,
                        "H264",
                        900,
                        POLICY_HASH,
                        PROBE_VERSION));

        assertRunnerFailsClosedWithoutSeed();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("untrustedMediaFacts")
    void mismatchedTrustedMediaFactStopsStartupBeforeEntitySeed(
            String caseName, VideoMediaInspection inspection) throws Exception {
        loadExactObjects();
        when(videoMediaProbe.inspect(anyString(), anyLong(), anyString()))
                .thenReturn(inspection);

        assertRunnerFailsClosedWithoutSeed();
    }

    @Test
    void businessFileSizeLimitStopsStartupBeforeEntitySeed() throws Exception {
        loadExactObjects();
        when(paramService.getString("file.maxSize.video", "2147483648")).thenReturn("1");

        assertRunnerFailsClosedWithoutSeed();
    }

    @Test
    void businessDurationPolicyStopsStartupBeforeEntitySeed() throws Exception {
        loadExactObjects();
        when(paramService.getInt("video.durationTarget", 900)).thenReturn(600);
        when(paramService.getInt("video.durationTolerance", 60)).thenReturn(0);

        assertRunnerFailsClosedWithoutSeed();
    }

    @Test
    void renderedSqlHasNoTokensAndCarriesTrustedRuntimeMetadata() throws Exception {
        loadExactObjects();
        DemoDataInitializer.ReconcileSummary summary =
                initializer.reconcileSampleObjects(BUCKET);

        String sql = initializer.renderDemoSql(summary);

        assertThat(sql)
                .doesNotContain("{{DEMO_")
                .contains(
                        "'" + BUCKET + "'",
                        Long.toString(VIDEO_SIZE),
                        "'" + VIDEO_FINGERPRINT + "'",
                        "'SHA256_TREE_V1'",
                        "'H264'",
                        "'" + POLICY_HASH + "'",
                        "'" + PROBE_VERSION + "'",
                        "'" + VIDEO_KEY + "'",
                        "'" + MATERIAL_KEY + "'",
                        "'" + IMAGE_KEY + "'",
                        "'" + EXEMPTION_KEY + "'",
                        "'" + MATERIAL_MD5 + "'",
                        "'" + IMAGE_MD5 + "'",
                        "content_hash_verified",
                        "media_validation_policy_hash",
                        "media_probe_version",
                        "uploaded_bytes = VALUES(uploaded_bytes)",
                        "duration_seconds = VALUES(duration_seconds)")
                .doesNotContain(
                        "'teacher-cert'",
                        "a1b2c3d4e5f60110101112131415161a",
                        "a1b2c3d4e5f60110101112131415162b",
                        "a1b2c3d4e5f60110101112131415163c",
                        "b1b2c3d4e5f60110101112131415160d",
                        "b1b2c3d4e5f60110101112131415161e",
                        "b1b2c3d4e5f60110101112131415162f",
                        "c1b2c3d4e5f60110101112131415160a",
                        "d1b2c3d4e5f60110101112131415160b",
                        "'" + LEGACY_VIDEO_KEY + "'",
                        "'" + LEGACY_MATERIAL_KEY + "'",
                        "'" + LEGACY_IMAGE_KEY + "'",
                        "'" + LEGACY_EXEMPTION_KEY + "'",
                        ", 528, 'video/mp4'",
                        ", 528, 905,",
                        ", 528, 890,",
                        ", 528, 910,");
        String fileObjectSql = sql.substring(
                sql.indexOf("INSERT INTO file_object"),
                sql.indexOf("INSERT INTO student"));
        assertThat(fileObjectSql)
                .contains(
                        "'" + VIDEO_KEY + "'",
                        "'" + MATERIAL_KEY + "'",
                        "'" + IMAGE_KEY + "'",
                        "'" + EXEMPTION_KEY + "'")
                .doesNotContain(
                        "'" + LEGACY_VIDEO_KEY + "'",
                        "'" + LEGACY_MATERIAL_KEY + "'",
                        "'" + LEGACY_IMAGE_KEY + "'",
                        "'" + LEGACY_EXEMPTION_KEY + "'");
        String processMaterialSql = sql.substring(
                sql.indexOf("INSERT INTO process_material"),
                sql.indexOf("INSERT INTO exemption_request"));
        assertThat(processMaterialSql)
                .contains("'" + MATERIAL_KEY + "'", "'" + IMAGE_KEY + "'")
                .doesNotContain("'" + LEGACY_MATERIAL_KEY + "'", "'" + LEGACY_IMAGE_KEY + "'");
        String exemptionMaterialSql = sql.substring(
                sql.indexOf("INSERT INTO exemption_material"),
                sql.indexOf("INSERT INTO video_review"));
        assertThat(exemptionMaterialSql)
                .contains("'" + EXEMPTION_KEY + "'")
                .doesNotContain("'" + LEGACY_EXEMPTION_KEY + "'");
        String uploadSessionSql = sql.substring(
                sql.indexOf("INSERT INTO video_upload_session"),
                sql.indexOf("INSERT INTO video_review_task"));
        assertThat(uploadSessionSql)
                .contains(
                        "object_key",
                        "'" + VIDEO_KEY + "'",
                        "object_key = VALUES(object_key)",
                        "uploaded_bytes = VALUES(uploaded_bytes)",
                        "duration_seconds = VALUES(duration_seconds)");
    }

    private DemoDataInitializer runnerSpy() throws Exception {
        DemoDataInitializer runner = spy(initializer);
        doNothing().when(runner).seedEntities(any(DemoDataInitializer.ReconcileSummary.class));
        return runner;
    }

    private void assertRunnerFailsClosedWithoutSeed() throws Exception {
        DemoDataInitializer runner = runnerSpy();

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("演示数据初始化失败，已终止启动")
                .hasNoCause();
        verify(runner, never()).seedEntities(any(DemoDataInitializer.ReconcileSummary.class));
    }

    private void assertTrustedVideoManifest(DemoDataInitializer.DemoVideoManifest manifest) {
        assertThat(manifest.bucket()).isEqualTo(BUCKET);
        assertThat(manifest.objectKey()).isEqualTo(VIDEO_KEY);
        assertThat(manifest.contentType()).isEqualTo("video/mp4");
        assertThat(manifest.size()).isEqualTo(VIDEO_SIZE);
        assertThat(manifest.fingerprint()).isEqualTo(VIDEO_FINGERPRINT);
        assertThat(manifest.durationSeconds()).isEqualTo(900);
        assertThat(manifest.checksumAlgorithm()).isEqualTo("SHA256_TREE_V1");
        assertThat(manifest.codec()).isEqualTo("H264");
        assertThat(manifest.policyHash()).isEqualTo(POLICY_HASH);
        assertThat(manifest.probeVersion()).isEqualTo(PROBE_VERSION);
    }

    private void loadExactObjects() throws IOException {
        objectStore.putDirect(VIDEO_KEY, resourceBytes(VIDEO_RESOURCE), "video/mp4");
        loadExactNonVideoObjects();
    }

    private void loadExactNonVideoObjects() throws IOException {
        objectStore.putDirect(MATERIAL_KEY, resourceBytes(MATERIAL_RESOURCE), "application/pdf");
        objectStore.putDirect(IMAGE_KEY, resourceBytes(IMAGE_RESOURCE), "image/png");
        objectStore.putDirect(EXEMPTION_KEY, resourceBytes(MATERIAL_RESOURCE), "application/pdf");
    }

    private void assertExactStoredObjects() throws IOException {
        assertThat(objectStore.get(VIDEO_KEY))
                .satisfies(object -> {
                    assertThat(object.bytes()).isEqualTo(resourceBytesUnchecked(VIDEO_RESOURCE));
                    assertThat(object.contentType()).isEqualTo("video/mp4");
                });
        assertThat(objectStore.get(MATERIAL_KEY))
                .satisfies(object -> {
                    assertThat(object.bytes()).isEqualTo(resourceBytesUnchecked(MATERIAL_RESOURCE));
                    assertThat(object.contentType()).isEqualTo("application/pdf");
                });
        assertThat(objectStore.get(IMAGE_KEY))
                .satisfies(object -> {
                    assertThat(object.bytes()).isEqualTo(resourceBytesUnchecked(IMAGE_RESOURCE));
                    assertThat(object.contentType()).isEqualTo("image/png");
                });
        assertThat(objectStore.get(EXEMPTION_KEY))
                .satisfies(object -> {
                    assertThat(object.bytes()).isEqualTo(resourceBytesUnchecked(MATERIAL_RESOURCE));
                    assertThat(object.contentType()).isEqualTo("application/pdf");
                });
    }

    private static byte[] resourceBytes(String path) throws IOException {
        return new ClassPathResource(path).getContentAsByteArray();
    }

    private static byte[] resourceBytesUnchecked(String path) {
        try {
            return resourceBytes(path);
        } catch (IOException e) {
            throw new AssertionError("测试资源无法读取: " + path, e);
        }
    }

    private static VideoMediaInspection trustedInspection() {
        return VideoMediaInspection.valid(
                VIDEO_FINGERPRINT,
                900,
                "H264",
                900,
                POLICY_HASH,
                PROBE_VERSION);
    }

    private static Stream<Arguments> untrustedMediaFacts() {
        String otherFingerprint = "0".repeat(64);
        String otherPolicyHash = "1".repeat(64);
        return Stream.of(
                Arguments.of(
                        "wrong fingerprint",
                        VideoMediaInspection.valid(
                                otherFingerprint, 900, "H264", 900, POLICY_HASH, PROBE_VERSION)),
                Arguments.of(
                        "wrong duration",
                        VideoMediaInspection.valid(
                                VIDEO_FINGERPRINT, 899, "H264", 900, POLICY_HASH, PROBE_VERSION)),
                Arguments.of(
                        "wrong codec",
                        VideoMediaInspection.valid(
                                VIDEO_FINGERPRINT, 900, "H265", 900, POLICY_HASH, PROBE_VERSION)),
                Arguments.of(
                        "wrong frame count",
                        VideoMediaInspection.valid(
                                VIDEO_FINGERPRINT, 900, "H264", 899, POLICY_HASH, PROBE_VERSION)),
                Arguments.of(
                        "wrong policy hash",
                        VideoMediaInspection.valid(
                                VIDEO_FINGERPRINT, 900, "H264", 900, otherPolicyHash, PROBE_VERSION)),
                Arguments.of(
                        "wrong probe version",
                        VideoMediaInspection.valid(
                                VIDEO_FINGERPRINT, 900, "H264", 900, POLICY_HASH, "OLD_PROBE")));
    }

    private static ErrorResponseException minioError(String code) {
        Request request = new Request.Builder()
                .url("http://127.0.0.1/" + BUCKET)
                .get()
                .build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code("AccessDenied".equals(code) ? 403 : 404)
                .message("simulated")
                .build();
        return new ErrorResponseException(
                new ErrorResponse(
                        code,
                        "simulated",
                        BUCKET,
                        null,
                        "/" + BUCKET,
                        "request-id",
                        "host-id"),
                response,
                "simulated-trace");
    }

    private static final class InMemoryObjectStore {

        private final MinioClient client;
        private final Map<String, StoredObject> objects = new LinkedHashMap<>();
        private final List<String> putKeys = new ArrayList<>();
        private Exception statFailure;
        private Exception getFailure;
        private Exception putFailure;
        private boolean corruptWrites;

        private InMemoryObjectStore(MinioClient client) {
            this.client = client;
        }

        private void install() throws Exception {
            when(client.statObject(any(StatObjectArgs.class)))
                    .thenAnswer(invocation -> stat(invocation.getArgument(0)));
            when(client.getObject(any(GetObjectArgs.class)))
                    .thenAnswer(invocation -> getObject(invocation.getArgument(0)));
            when(client.putObject(any(PutObjectArgs.class)))
                    .thenAnswer(invocation -> putObject(invocation.getArgument(0)));
        }

        private StatObjectResponse stat(StatObjectArgs args) throws Exception {
            if (statFailure != null) {
                throw statFailure;
            }
            StoredObject object = objects.get(args.object());
            if (object == null) {
                throw minioError("NoSuchKey");
            }
            StatObjectResponse response = org.mockito.Mockito.mock(StatObjectResponse.class);
            when(response.size()).thenReturn((long) object.bytes().length);
            when(response.contentType()).thenReturn(object.contentType());
            return response;
        }

        private GetObjectResponse getObject(GetObjectArgs args) throws Exception {
            if (getFailure != null) {
                throw getFailure;
            }
            StoredObject object = objects.get(args.object());
            if (object == null) {
                throw minioError("NoSuchKey");
            }
            return new GetObjectResponse(
                    new Headers.Builder().build(),
                    args.bucket(),
                    "us-east-1",
                    args.object(),
                    new ByteArrayInputStream(object.bytes()));
        }

        private Object putObject(PutObjectArgs args) throws Exception {
            if (putFailure != null) {
                throw putFailure;
            }
            byte[] bytes = args.stream().readAllBytes();
            if (corruptWrites && bytes.length > 0) {
                bytes[0] ^= 0x01;
            }
            objects.put(args.object(), new StoredObject(bytes, args.contentType()));
            putKeys.add(args.object());
            return null;
        }

        private void putDirect(String key, byte[] bytes, String contentType) {
            objects.put(key, new StoredObject(bytes, contentType));
        }

        private StoredObject get(String key) {
            return objects.get(key);
        }

        private List<String> putKeys() {
            return List.copyOf(putKeys);
        }

        private void statFailure(Exception failure) {
            this.statFailure = failure;
        }

        private void getFailure(Exception failure) {
            this.getFailure = failure;
        }

        private void putFailure(Exception failure) {
            this.putFailure = failure;
        }

        private void corruptWrites() {
            this.corruptWrites = true;
        }
    }

    private record StoredObject(byte[] bytes, String contentType) {

        private StoredObject {
            bytes = Arrays.copyOf(bytes, bytes.length);
        }
    }
}
