package cn.edu.gpnu.platform.business.student.support;

import org.springframework.util.StringUtils;

public final class SensitiveMasker {

    private SensitiveMasker() {
    }

    public static String idCard(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String text = value.trim();
        if (text.length() <= 10) {
            return "*".repeat(Math.max(0, text.length()));
        }
        return text.substring(0, 6) + "*".repeat(text.length() - 10) + text.substring(text.length() - 4);
    }

    public static String phone(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() < 7) {
            return value;
        }
        String text = value.trim();
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }
}
