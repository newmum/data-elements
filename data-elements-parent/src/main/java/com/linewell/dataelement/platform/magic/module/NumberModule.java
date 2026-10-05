package com.linewell.dataelement.platform.magic.module;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * @Description: 数字模块
 * @Author: gaoZhenWen
 * @Date: 2023/2/2 10:20
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Component
@MagicModule("nums")
@Slf4j
public class NumberModule {
    /**
     * @Description: 数字格式化，去掉小数点尾缀的0
     * @Author: gaoZhenWen
     * @Date: 2023/2/2 10:31
     * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
     */
    @Comment("数字格式化，去掉尾缀的0")
    public String format(@Comment("数字格式化，去掉尾缀的0") Object num) {
        if (num == null) {
            return "";
        }
        if (num instanceof BigDecimal) {
            return ((BigDecimal)num).stripTrailingZeros().toPlainString();
        }
        if (num instanceof Double) {
            return new BigDecimal((Double) num).toPlainString();
        }
        if (num instanceof Float) {
            return new BigDecimal((Float) num).toPlainString();
        }
        if (num instanceof Integer) {
            return num + "";
        }
        if (num instanceof Long) {
            return num + "";
        }
        BigDecimal res = new BigDecimal(num + "").stripTrailingZeros();
        return res.toPlainString();
    }
    /**
     * @Description: 字符串转数字
     * @Author: gaoZhenWen
     * @Date: 2023/2/2 10:31
     * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
     */
    @Comment("字符串转数字")
    public BigDecimal toNum(@Comment("字符串") Object num) {
        if (num == null) {
            return null;
        }
        if (num instanceof String) {
            return new BigDecimal((String) num);
        }
        return new BigDecimal(num + "");
    }

    /**
     * @Description: 装换单位
     * @Author: gaoZhenWen
     * @Date: 2023/2/10 13:39
     * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
     */
    @Comment("数字单位转换，如转万 转亿")
    public String formatUnit(@Comment("数字") Object num) {
        if (num == null) {
            return "0";
        }
        BigDecimal value = null;
        if (num instanceof String) {
            value = new BigDecimal((String) num);
        }
        if (num instanceof BigDecimal) {
            value = (BigDecimal) num;
        }
        if (num instanceof Long) {
            value = new BigDecimal((Long) num);
        }
        if (num instanceof Integer) {
            value = new BigDecimal((Integer) num);
        }
        if (value == null) {
            value = new BigDecimal(num + "");
        }
        //小于10000
        if (value.compareTo(new BigDecimal(10000)) < 0) {
            return value.toPlainString();
        }
        //小于一个亿 展示 100万 1000.34万条
        if (value.compareTo(new BigDecimal(10000 * 10000)) < 0) {
            return value.divide(new BigDecimal(10000),2, BigDecimal.ROUND_HALF_UP) + "万";
        }
        //大于一个亿 展示多少个亿  100.3亿条
        return value.divide(new BigDecimal(10000 * 10000),2, BigDecimal.ROUND_HALF_UP) + "亿";
    }
}
