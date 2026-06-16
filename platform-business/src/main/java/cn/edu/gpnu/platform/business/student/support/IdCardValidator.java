package cn.edu.gpnu.platform.business.student.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class IdCardValidator {

    public static final String RESIDENT_ID_CARD = "resident_id_card";
    public static final String HMT_RESIDENCE_PERMIT = "hmt_residence_permit";
    public static final String HM_TRAVEL_PERMIT = "hm_travel_permit";
    public static final String TW_TRAVEL_PERMIT_5Y = "tw_travel_permit_5y";
    private static final String MISMATCH = "证件类型与号码不匹配";
    private static final Map<String, Pattern> PATTERNS = Map.of(
            RESIDENT_ID_CARD, Pattern.compile("^\\d{17}[\\dXx]$"),
            HMT_RESIDENCE_PERMIT, Pattern.compile("^\\d{17}[\\dXx]$"),
            HM_TRAVEL_PERMIT, Pattern.compile("^[A-Za-z]\\d{8}$"),
            TW_TRAVEL_PERMIT_5Y, Pattern.compile("^\\d{8}$")
    );
    private static final int[] CHECK_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private final ParamService paramService;

    public String validate(String type, String number) {
        String normalizedType = normalize(type);
        String normalizedNumber = normalize(number);
        Pattern pattern = PATTERNS.get(normalizedType);
        if (pattern == null || !StringUtils.hasText(normalizedNumber) || !pattern.matcher(normalizedNumber).matches()) {
            throw new BizException(MISMATCH);
        }
        if ((RESIDENT_ID_CARD.equals(normalizedType) || HMT_RESIDENCE_PERMIT.equals(normalizedType))
                && normalizedNumber.endsWith("x")) {
            normalizedNumber = normalizedNumber.substring(0, normalizedNumber.length() - 1) + "X";
        }
        if (RESIDENT_ID_CARD.equals(normalizedType) && paramService.getBoolean("validate.idcard.checksum", false)
                && !validChecksum(normalizedNumber)) {
            throw new BizException(MISMATCH);
        }
        return normalizedNumber;
    }

    public boolean requiresBirthDateCheck(String type) {
        String normalizedType = normalize(type);
        return RESIDENT_ID_CARD.equals(normalizedType) || HMT_RESIDENCE_PERMIT.equals(normalizedType);
    }

    private boolean validChecksum(String number) {
        int sum = 0;
        for (int i = 0; i < CHECK_WEIGHTS.length; i++) {
            sum += (number.charAt(i) - '0') * CHECK_WEIGHTS[i];
        }
        return CHECK_CODES[sum % 11] == Character.toUpperCase(number.charAt(17));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
