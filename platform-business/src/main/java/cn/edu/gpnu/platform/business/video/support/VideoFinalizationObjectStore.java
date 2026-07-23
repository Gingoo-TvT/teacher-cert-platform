package cn.edu.gpnu.platform.business.video.support;

public interface VideoFinalizationObjectStore {

    void remove(String bucket, String objectKey);

    boolean exists(String bucket, String objectKey);
}
