package com.linewell.dataelement.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class EsPageInfo<T>{
    public static final int DEFAULT_NAVIGATE_PAGES = 8;
    //当前页
    private int pageNum;
    //每页的数量
    private int pageSize;
    //总记录数
    private long total;
    //结果集
    private List<T> list;

    public EsPageInfo(int pageNum, int pageSize, long total, List<T> list) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.list = list;
    }

    public static EsPageInfo empty(int pageNum, int pageSize){
        return new EsPageInfo<>(pageNum, pageSize, 0, new ArrayList<>());
    }

}