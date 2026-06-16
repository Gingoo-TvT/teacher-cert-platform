package cn.edu.gpnu.platform.business.student.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class NameValidator {

    private static final Pattern STRICT = Pattern.compile("^[一-龥·]{2,}$");
    private static final Pattern LOOSE_FORBIDDEN = Pattern.compile("[\\s\\dA-Za-z\\p{Punct}&&[^·]]");
    private final ParamService paramService;

    public void validate(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("姓名不能为空");
        }
        String text = name.trim();
        String mode = paramService.getString("validate.name.mode", "loose");
        boolean valid = "strict".equalsIgnoreCase(mode)
                ? STRICT.matcher(text).matches()
                : text.length() >= 2 && !LOOSE_FORBIDDEN.matcher(text).find();
        if (!valid) {
            throw new BizException("姓名格式异常");
        }
    }
}
