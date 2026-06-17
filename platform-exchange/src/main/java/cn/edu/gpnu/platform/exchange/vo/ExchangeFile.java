package cn.edu.gpnu.platform.exchange.vo;

public record ExchangeFile(String fileName, String contentType, byte[] content) {
}
