package cn.edu.gpnu.platform.file.model;

public record MultipartUploadedPart(int partNumber, String eTag, long size) {
}
