package cn.edu.gpnu.platform.statistics.vo;

public record StatsExportFile(String fileName, String contentType, byte[] content) {
}
