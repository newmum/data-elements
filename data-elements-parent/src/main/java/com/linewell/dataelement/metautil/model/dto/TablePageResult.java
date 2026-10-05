package com.linewell.dataelement.metautil.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 表/视图分页探查结果。
 *
 * <p>登记流程第二步只需要按页展示表级摘要，不应该一次性把几千张表全部返回给前端渲染。
 * total 表示物理库里符合条件的总表/视图数量，rows 只返回当前页数据。</p>
 */
@Data
public class TablePageResult {

    /** 当前页码，从 1 开始。 */
    private Integer pageNo;

    /** 每页条数。 */
    private Integer pageSize;

    /** 符合条件的总表/视图数量。 */
    private Long total;

    /** 当前页表/视图摘要。 */
    private List<TableInfo> rows = new ArrayList<>();
}
