package cn.edu.gpnu.platform.file.model;

public record DirectFileUploadPlan(Long fileId, long partSize, MultipartUploadPlan upload, boolean ready) {

    public static DirectFileUploadPlan uploading(Long fileId, long partSize, MultipartUploadPlan upload) {
        return new DirectFileUploadPlan(fileId, partSize, upload, false);
    }

    public static DirectFileUploadPlan ready(Long fileId, long partSize) {
        return new DirectFileUploadPlan(fileId, partSize, null, true);
    }
}
