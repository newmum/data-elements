package com.linewell.dataelement.elasticsearch;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * @author zwenbo
 * @Description: TODO
 * @date 2026/1/29
 */
@Data
public class EsPageParam implements Serializable {
    //查询索引名称
    private String index;
    //查询字段条件
    private List<ConditionDTO> conditions;
    //分页
    private int pageNum = 1;
    //分页条数
    private int pageSize = 10;
    //排序字段
    private String sortField;
    //排序方向 asc desc
    private String sortDir;
}
