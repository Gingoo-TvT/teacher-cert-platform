package cn.edu.gpnu.platform.business.student.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            throw new BizException("出生日期格式异常");
        }
        return matcher.group(1) + "%02d".formatted(month) + "%02d".formatted(day);
    }
}
