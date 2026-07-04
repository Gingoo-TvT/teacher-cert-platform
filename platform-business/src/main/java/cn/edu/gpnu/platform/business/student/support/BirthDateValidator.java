package cn.edu.gpnu.platform.business.student.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class BirthDateValidator {

    private static final Pattern TEXT_DATE = Pattern.compile("^(\\d{4})[-/年.](\\d{1,2})[-/月.](\\d{1,2})日?$");
    private final IdCardValidator idCardValidator;

    public void validate(String idCardType, String idCardNo, String birthDate) {
        if (!idCardValidator.requiresBirthDateCheck(idCardType)) {
            return;
        }
        String normalizedBirthDate = normalizeBirthDate(birthDate);
        if (!StringUtils.hasText(idCardNo) || idCardNo.trim().length() < 14
                || !idCardNo.trim().substring(6, 14).equals(normalizedBirthDate)) {
            throw new BizException("出生日期与证件号码不一致");
        }
    }

    public String normalizeBirthDate(String birthDate) {
        if (!StringUtils.hasText(birthDate)) {
            throw new BizException("出生日期不能为空");
        }
        String text = birthDate.trim();
        Matcher matcher = TEXT_DATE.matcher(text);
        if (!matcher.matches()) {
            throw new BizException("出生日期格式异常");
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        try {
            // 用 LocalDate 的日历规则做严格校验（含闰年 2 月 29 日、大小月），拒绝 2023-02-30 这类看似合法实非法的日期。
            LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new BizException("出生日期格式异常");
        }
        return matcher.group(1) + "%02d".formatted(month) + "%02d".formatted(day);
    }
}
