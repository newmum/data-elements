package com.linewell.dataelement.platform.persistence;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Orders bounded browser drafts/layouts. It reads no database objects and returns only supplied keys.
 * Persisted business lists must use their own authorized database page API instead. */
@RestController
@UseControlDataSource
@RequestMapping({"/sym/table","/idaas/workspace"})
public class TablePreviewOrderController {
    public record Item(String id,Object value) {}
    public record Query(List<Item> items,String kind,String direction,Integer page,Integer size,Boolean wholeCollection) {
        public Query(List<Item> items,String kind,String direction,Integer page,Integer size) {
            this(items,kind,direction,page,size,false);
        }
    }

    @PostMapping("/preview-order")
    public CommonResponse<Map<String,Object>> order(@RequestBody Query query,HttpServletRequest request) {
        if(request.getRequestURI().startsWith("/idaas/"))PlatformSessionMagicModule.LOGIC.checkLogin();
        else StpUtil.checkLogin();
        return new CommonResponse<>(ordered(query));
    }

    static Map<String,Object> ordered(Query query) {
        if(query==null||query.items()==null||query.items().size()>1000)throw new IllegalArgumentException("预览排序最多支持1000项，请缩小视图范围");
        String kind=query.kind()==null?"text":query.kind();
        if(!Set.of("text","number","time").contains(kind))throw new IllegalArgumentException("无效的预览排序类型");
        boolean whole=Boolean.TRUE.equals(query.wholeCollection());
        int page=whole?1:query.page()==null?1:query.page(),size=whole?1000:query.size()==null?20:query.size();
        if(page<1||size<1||size>(whole?1000:100))throw new IllegalArgumentException("预览分页参数无效");
        Set<String> ids=new HashSet<>();List<Map<String,Object>> rows=new ArrayList<>();
        for(Item item:query.items()) {
            if(item==null||item.id()==null||item.id().isBlank()||item.id().length()>256||!ids.add(item.id()))throw new IllegalArgumentException("预览行标识无效或重复");
            Object value=item.value();
            if(value!=null) {
                if(!(value instanceof String||value instanceof Number)||value.toString().length()>1024)throw new IllegalArgumentException("预览排序值无效");
                if(value.toString().isBlank())value=null;
                else if(kind.equals("number"))value=new BigDecimal(value.toString());
                else if(kind.equals("time"))value=Instant.parse(value.toString()).toEpochMilli();
                else value=value.toString();
            }
            Map<String,Object> row=new HashMap<>();row.put("id",item.id());row.put("value",value);rows.add(row);
        }
        Map<String,Object> sort=new HashMap<>();sort.put("sortDir",query.direction()==null?"asc":query.direction());
        var ordered=new TableSortMagicModule().rows(rows,sort,Map.of("value","value"),"value","id");
        long offset=Math.multiplyExact((long)page-1,size);int start=(int)Math.min(offset,ordered.size()),end=Math.min(start+size,ordered.size());
        return Map.of("keys",ordered.subList(start,end).stream().map(row->row.get("id")).toList(),"total",rows.size());
    }
}
