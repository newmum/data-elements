package com.linewell.dataelement.elasticsearch;

/**
 * @author zwenbo
 * @Description: TODO
 * @date 2026/1/29
 */

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConditionDTO implements Serializable {
    /**
     * 对应 ES 中的字段名
     */
    private String field;

    /**
     * 查询内容
     */
    private Object value;

    /**
     * 查询类型：精确 (exact) / 模糊 (fuzzy)
     */
    private String type;
}

