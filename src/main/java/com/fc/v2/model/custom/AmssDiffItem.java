package com.fc.v2.model.custom;

/**
 * 通行费免收对账差异项代号。一项一个码，报送册上只准用这五种。
 *
 * @author fuce
 * @date 2026-09-27
 */
public enum AmssDiffItem {

    /** 无证通行 */
    NO_CARD("无证通行"),

    /** 证号与机主对不上 */
    MISMATCH("证号与机主对不上"),

    /** 未预约通行 */
    NO_BOOK("未预约通行"),

    /** 一证多车 */
    MULTI_CAR("一证多车"),

    /** 跨省归属不符 */
    CROSS_PROV("跨省归属不符");

    private final String label;

    AmssDiffItem(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    /** 代号是否在五种规定情形之内（空代号与未知代号一律不认） */
    public static boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        for (AmssDiffItem item : values()) {
            if (item.name().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
