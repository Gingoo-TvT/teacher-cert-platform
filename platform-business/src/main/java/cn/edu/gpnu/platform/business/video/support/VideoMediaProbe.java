package cn.edu.gpnu.platform.business.video.support;

public interface VideoMediaProbe {

    String CHECKSUM_ALGORITHM = "SHA256_TREE_V1";

    VideoMediaInspection inspect(String objectKey, long expectedSize, String declaredFingerprint);
}
