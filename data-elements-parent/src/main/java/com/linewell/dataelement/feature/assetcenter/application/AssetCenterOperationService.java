package com.linewell.dataelement.feature.assetcenter.application;

import static com.linewell.dataelement.feature.assetcenter.application.AssetCenterStore.*;

import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Operations whose contract cannot be fulfilled by a read-only projection. */
@Service
public class AssetCenterOperationService {
    private final AssetCenterStore db;
    private final AssetCenterQueryService queries;
    private final AssetCenterWorkbenchService workbench;
    private final RegisteredJdbcDataSourceResolver sources;

    public AssetCenterOperationService(AssetCenterStore db, AssetCenterQueryService queries,
            AssetCenterWorkbenchService workbench, RegisteredJdbcDataSourceResolver sources) {
        this.db = db;
        this.queries = queries;
        this.workbench = workbench;
        this.sources = sources;
    }

    public Object importPreview(String catalogId, Map<String,Object> input) {
        db.manage(db.owned("da_catalog_t", catalogId, false));
        throw error(503, "UPSTREAM_UNAVAILABLE", "受控 Excel 文件服务尚未接入，暂不能导入数据项");
    }

    /** Parse an Excel template into editor data; saving still uses the canonical catalog registration API. */
    public Object parseImport(MultipartFile file) {
        db.tenant();db.user();
        if(file==null||file.isEmpty()||file.getSize()>5_000_000)throw error(400,"IMPORT_INVALID","请选择不超过5 MB的 Excel 文件");
        String filename=file.getOriginalFilename()==null?"":file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        if(!filename.endsWith(".xlsx")&&!filename.endsWith(".xls"))throw error(400,"IMPORT_INVALID","仅支持 .xlsx 或 .xls 文件");
        List<Map<String,Object>> items=new ArrayList<>(),issues=new ArrayList<>();
        Set<String> codes=new HashSet<>();
        try(var book=WorkbookFactory.create(file.getInputStream())) {
            if(book.getNumberOfSheets()==0)throw error(400,"IMPORT_INVALID","Excel 文件没有工作表");
            var sheet=book.getSheetAt(0);var header=sheet.getRow(sheet.getFirstRowNum());
            if(header==null)throw error(400,"IMPORT_INVALID","Excel 模板为空");
            var formatter=new DataFormatter(java.util.Locale.CHINA);
            Map<String,Integer> indexes=new LinkedHashMap<>();
            for(Cell cell:header)indexes.put(formatter.formatCellValue(cell).trim().replaceAll("\\s+",""),cell.getColumnIndex());
            int nameIndex=header(indexes,"数据项名称","字段名称","中文名","名称"),codeIndex=header(indexes,"英文代码","字段编码","英文名","技术标识"),typeIndex=header(indexes,"数据类型","字段类型","类型");
            if(nameIndex<0||codeIndex<0||typeIndex<0)throw error(400,"IMPORT_INVALID","表头需包含“数据项名称、英文代码、数据类型”三列");
            int lengthIndex=header(indexes,"长度","字段长度"),descriptionIndex=header(indexes,"说明","业务说明","字段说明"),primaryIndex=header(indexes,"主键","是否主键"),nullableIndex=header(indexes,"可空","是否可空");
            if(sheet.getLastRowNum()-sheet.getFirstRowNum()>5000)throw error(400,"IMPORT_INVALID","一次最多导入5000条数据项");
            for(int rowNo=header.getRowNum()+1;rowNo<=sheet.getLastRowNum();rowNo++) {
                Row row=sheet.getRow(rowNo);if(row==null)continue;
                String name=cell(row,nameIndex,formatter),code=cell(row,codeIndex,formatter),type=cell(row,typeIndex,formatter),length=cell(row,lengthIndex,formatter),description=cell(row,descriptionIndex,formatter);
                if(name.isBlank()&&code.isBlank()&&type.isBlank())continue;
                int excelRow=rowNo+1;
                if(name.isBlank()||name.length()>255)issue(issues,excelRow,"name","数据项名称不能为空且不能超过255字");
                if(!code.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))issue(issues,excelRow,"code","英文代码需以字母或下划线开头，仅包含字母、数字和下划线");
                if(!code.isBlank()&&!codes.add(code.toLowerCase(java.util.Locale.ROOT)))issue(issues,excelRow,"code","文件中英文代码重复");
                if(type.isBlank()||type.length()>64)issue(issues,excelRow,"dataType","数据类型不能为空且不能超过64字");
                Integer parsedLength=null;
                if(!length.isBlank()){try{parsedLength=Integer.valueOf(length);if(parsedLength<0||parsedLength>1_000_000)throw new NumberFormatException();}catch(NumberFormatException invalid){issue(issues,excelRow,"length","长度需为0到1000000之间的整数");}}
                if(formula(row))issue(issues,excelRow,"formula","模板不能包含公式，请粘贴为数值后再导入");
                items.add(map("colName",name,"colEn",code,"colType",type,"colLength",parsedLength,"colComment",description,"isPk",yes(cell(row,primaryIndex,formatter))?"1":"0","isNullable",nullableIndex<0||yes(cell(row,nullableIndex,formatter))?"1":"0"));
            }
        }catch(AssetCenterException failure){throw failure;}
        catch(IOException|RuntimeException failure){throw error(400,"IMPORT_INVALID","Excel 文件无法解析，请检查文件格式");}
        return map("items",items,"totalRows",items.size(),"validRows",issues.isEmpty()?items.size():0,"issues",issues);
    }

    private int header(Map<String,Integer> columns,String...names){for(String name:names)if(columns.containsKey(name))return columns.get(name);return -1;}
    private String cell(Row row,int index,DataFormatter formatter){if(index<0)return "";Cell cell=row.getCell(index);return cell==null?"":formatter.formatCellValue(cell).trim();}
    private boolean formula(Row row){for(Cell cell:row)if(cell.getCellType()==CellType.FORMULA)return true;return false;}
    private boolean yes(String value){return Set.of("是","1","true","TRUE","Y","yes","YES").contains(value);}
    private void issue(List<Map<String,Object>> issues,int row,String field,String message){issues.add(map("rowNumber",row,"field",field,"code","IMPORT_INVALID","message",message,"severity","ERROR"));}

    public Object importCommit(String catalogId, Map<String,Object> input, String key) {
        db.manage(db.owned("da_catalog_t", catalogId, false));
        throw error(503, "UPSTREAM_UNAVAILABLE", "受控 Excel 文件服务尚未接入，暂不能导入数据项");
    }

    public Object deliveryAccess(String deliveryId, Map<String,Object> input, String key) {
        db.require("da_authorization_scope_t");
        var grant = db.owned("da_authorization_scope_t", deliveryId, false);
        if (!db.user().equals(text(grant,"grantee_user_id"))
                && !db.organizations().contains(text(grant,"grantee_org_id")))
            throw error(404,"NOT_FOUND","交付资源不存在或无权访问");
        if (!"ACTIVE".equals(text(grant,"status")))
            throw error(409,"GRANT_INACTIVE","授权尚未生效或已失效");
        throw error(503,"DELIVERY_NOT_READY","交付接入点尚未完成受控配置");
    }

    @Transactional
    public Object demandResponse(String demandId, Map<String,Object> input, String key) {
        return db.idempotent("DEMAND_RESPONSE:"+demandId,key,input,()->{
            var form=db.owned("data_apply_form_t",demandId,true);
            var demand=demandCase(demandId,true);
            db.revision(form,input);
            String action=required(input,"action",32),content=required(input,"content",4000);
            if(!Set.of("ACCEPT","COMMENT","RECOMMEND","RETURN_FOR_INFO","MARK_UNFULFILLED").contains(action))
                throw error(400,"INVALID_ARGUMENT","需求处理动作无效");
            canHandle(form,demand,action);
            if(Set.of("DRAFT","WITHDRAWN","CLOSED","FULFILLED").contains(text(demand,"status")))
                throw error(409,"INVALID_STATE","当前需求不能继续对接");
            if("RECOMMEND".equals(action)) {
                var candidates=db.array(input.get("candidates"));
                if(candidates.isEmpty()||candidates.size()>30)throw error(400,"INVALID_ARGUMENT","请选择最多30个已上架目录");
                for(var candidate:candidates) {
                    validateCandidate(candidate,text(form,"apply_org_id"));
                    append(demand,"PROPOSED",content,candidate,key);
                }
            } else append(demand,switch(action){case "ACCEPT"->"ACCEPTED";case "COMMENT"->"COMMENT";case "RETURN_FOR_INFO","MARK_UNFULFILLED"->"DECLINED";default->"COMMENT";},content,map("action",action),key);
            String status=switch(action){case "ACCEPT"->"TRIAGED";case "RECOMMEND"->"MATCHING";case "MARK_UNFULFILLED"->"CLOSED";default->text(demand,"status");};
            db.jdbc.update("UPDATE da_demand_case_t SET status=?,close_reason=?,closed_at=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",
                    status,"MARK_UNFULFILLED".equals(action)?content:null,"MARK_UNFULFILLED".equals(action)?now():null,now(),db.user(),db.tenant(),demand.get("tid"));
            db.jdbc.update("UPDATE data_apply_form_t SET revision=COALESCE(revision,0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",now(),db.user(),db.tenant(),demandId);
            return workbench.demand(demandId);
        });
    }

    @Transactional
    public Object demandMatch(String demandId, Map<String,Object> input, String key) {
        return db.idempotent("DEMAND_MATCH:"+demandId,key,input,()->{
            var form=db.owned("data_apply_form_t",demandId,true);
            var demand=demandCase(demandId,true);
            db.revision(form,input);
            if(!db.user().equals(text(form,"apply_user_id")))throw error(403,"FORBIDDEN","只有需求提出人可以确认匹配");
            if(!Set.of("MATCHING","TRIAGED","SUBMITTED").contains(text(demand,"status")))throw error(409,"INVALID_STATE","当前需求不可确认匹配");
            String confirmation=required(input,"confirmation",4000);
            var candidates=db.array(input.get("candidates"));
            if(candidates.isEmpty()||candidates.size()>30)throw error(400,"INVALID_ARGUMENT","请选择最多30个匹配目录");
            var proposed=new HashSet<String>();
            for(var row:db.rows("SELECT event_meta FROM da_demand_match_t WHERE tenant_id=? AND demand_case_id=? AND event_type='PROPOSED'",db.tenant(),demand.get("tid"))) {
                var item=db.object(row.get("event_meta"));proposed.add(text(item,"catalogId")+":"+text(item,"versionId"));
            }
            for(var candidate:candidates) {
                validateCandidate(candidate,text(form,"apply_org_id"));
                if(!proposed.contains(text(candidate,"catalogId")+":"+text(candidate,"versionId")))throw error(400,"INVALID_ARGUMENT","只能确认已推荐的目录版本");
                append(demand,"ACCEPTED",confirmation,candidate,key);
            }
            db.jdbc.update("UPDATE da_demand_case_t SET status='MATCHED',last_match_at=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",now(),now(),db.user(),db.tenant(),demand.get("tid"));
            db.jdbc.update("UPDATE data_apply_form_t SET revision=COALESCE(revision,0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",now(),db.user(),db.tenant(),demandId);
            String draftId=null;
            if(Boolean.TRUE.equals(input.get("createSubscriptionDraft"))) {
                var first=candidates.get(0);var demandPayload=db.object(form.get("asset_payload_json"));
                var draft=workbench.draft(map("expectedRevision","0","title","由需求「"+text(form,"apply_name")+"」创建的资源订阅","actingDepartmentId",text(form,"apply_org_id"),"requestType","CATALOG_SUBSCRIPTION","businessScenario",demandPayload.get("scenario"),"recommendedCatalogId",first.get("catalogId"),"recommendedVersionId",first.get("versionId"),"resources",List.of()),key+":subscription",false);
                draftId=text((Map<String,Object>)draft,"id");
            }
            return map("demand",workbench.demand(demandId),"subscriptionDraftId",draftId);
        });
    }

    @Transactional
    public Object withdrawDemand(String demandId, Map<String,Object> input, String key) {
        return db.idempotent("DEMAND_WITHDRAW:"+demandId,key,input,()->{
            var form=db.owned("data_apply_form_t",demandId,true);var demand=demandCase(demandId,true);
            db.revision(form,input);
            if(!db.user().equals(text(form,"apply_user_id")))throw error(403,"FORBIDDEN","只有需求提出人可以撤回");
            if(!Set.of("SUBMITTED","TRIAGED","MATCHING").contains(text(demand,"status")))throw error(409,"INVALID_STATE","当前需求不可撤回");
            String reason=required(input,"reason",2000);
            db.jdbc.update("UPDATE da_demand_case_t SET status='WITHDRAWN',close_reason=?,closed_at=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",reason,now(),now(),db.user(),db.tenant(),demand.get("tid"));
            db.jdbc.update("UPDATE data_apply_form_t SET revision=COALESCE(revision,0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",now(),db.user(),db.tenant(),demandId);
            return workbench.demand(demandId);
        });
    }

    public Object preview(Map<String,Object> input) {
        var ref=db.object(input.get("object"));
        String kind=required(ref,"kind",32),id=required(ref,"id",64);
        if(!Set.of("TABLE","CATALOG").contains(kind))throw error(400,"INVALID_ARGUMENT","该资产不支持数据预览");
        int limit=Math.min(10,Math.max(1,number(input.get("limit"),10)));
        String tableId=id;
        List<Map<String,Object>> mappings=List.of();
        if(kind.equals("TABLE"))db.manage(db.owned("db_table_t",id,false));
        else {
            var catalog=db.owned("da_catalog_t",id,false);
            String versionId=required(input,"catalogVersionId",64);
            var version=db.one("SELECT * FROM da_catalog_publication_t WHERE tenant_id=? AND tid=? AND catalog_id=? AND status='PUBLISHED'",db.tenant(),versionId,id);
            if(version==null)throw error(404,"NOT_FOUND","可预览的目录版本不存在");
            var head=db.one("SELECT current_publication_id,availability FROM da_catalog_publication_head_t WHERE tenant_id=? AND catalog_id=?",db.tenant(),id);
            if(!db.canManage(catalog)&&(head==null||!"PUBLISHED".equals(text(head,"availability"))||!versionId.equals(text(head,"current_publication_id"))))
                throw error(403,"FORBIDDEN","目录未上架或不可预览");
            var policy=db.object(version.get("visibility_scope"));
            if(!db.canManage(catalog)&&!visible(policy,catalog))throw error(403,"FORBIDDEN","当前部门不可预览该目录");
            if(!"MASKED_SAMPLE".equals(text(policy,"previewMode")))throw error(403,"FORBIDDEN","该目录未开放数据预览");
            String binding=required(input,"attachmentId",64);
            var snap=db.one("SELECT * FROM da_catalog_resource_snapshot_t WHERE tenant_id=? AND publication_id=? AND binding_id=? AND resource_type='TABLE'",db.tenant(),versionId,binding);
            if(snap==null)throw error(400,"INVALID_ARGUMENT","请选择目录发布版本中挂接的数据表");
            tableId=text(snap,"resource_id");
            mappings=db.array(db.object(snap.get("contract_snapshot")).get("mappings"));
            if(mappings.isEmpty())throw error(422,"PRECHECK_FAILED","该目录数据项没有配置字段映射");
        }
        var table=db.owned("db_table_t",tableId,false);
        String sourceId=required(table,"datasource_id",64);
        var fields=db.rows("SELECT * FROM db_table_column_t WHERE tenant_id=? AND table_id=? AND COALESCE(is_del,0)=0 ORDER BY ordinal_position,tid LIMIT 100",db.tenant(),tableId);
        if(fields.isEmpty())throw error(422,"PRECHECK_FAILED","表尚未采集字段");
        List<Map<String,Object>> selected=new ArrayList<>();
        if(kind.equals("TABLE"))selected.addAll(fields.subList(0,Math.min(50,fields.size())));
        else {
            Set<String> ids=new HashSet<>();for(var mapping:mappings)if(!text(mapping,"resourceFieldId").isBlank())ids.add(text(mapping,"resourceFieldId"));
            for(var field:fields)if(ids.contains(text(field,"tid")))selected.add(field);
            if(selected.isEmpty())throw error(422,"PRECHECK_FAILED","目录映射的字段不在当前表结构中");
        }
        var endpoint=sources.resolve(db.tenant(),sourceId,tableId);
        List<Map<String,Object>> columns=new ArrayList<>(),rows=new ArrayList<>();
        boolean masked=false;
        try(Connection connection=sources.open(endpoint,5)) {
            connection.setReadOnly(true);
            String quote=connection.getMetaData().getIdentifierQuoteString().trim();
            String names=String.join(",",selected.stream().map(f->identifier(text(f,"column_name"),quote)).toList());
            String tableName=identifier(endpoint.tableName(),quote);
            try(PreparedStatement statement=connection.prepareStatement("SELECT "+names+" FROM "+tableName)) {
                statement.setMaxRows(limit);statement.setQueryTimeout(10);
                try(ResultSet result=statement.executeQuery()) {
                    for(var field:selected){String fieldId=text(field,"tid");columns.add(map("id",fieldId,"name",text(field,"column_comment").isBlank()?text(field,"column_name"):text(field,"column_comment"),"code",text(field,"column_name"),"dataType",text(field,"data_type"),"length",field.get("length"),"nullable",field.get("nullable"),"primaryKey",field.get("primary_key"),"ordinal",number(field.get("ordinal_position"),1)));}
                    while(result.next()&&rows.size()<limit){List<Map<String,Object>> cells=new ArrayList<>();for(int i=0;i<selected.size();i++){var field=selected.get(i);String value=result.getString(i+1);boolean hide=number(field.get("is_masking"),0)==1||sensitive(text(field,"column_name"),text(field,"column_comment"));if(hide&&value!=null){value="******";masked=true;}else if(value!=null&&value.length()>512)value=value.substring(0,512)+"…";cells.add(map("itemId",field.get("tid"),"value",value));}rows.add(map("cells",cells));}
                }
            }
        }catch(SQLException exception){throw error(503,"UPSTREAM_UNAVAILABLE","数据源预览暂不可用，请检查数据源连接与只读权限");}
        return map("columns",columns,"rows",rows,"sampledAt",iso(now()),"masked",masked,"truncated",rows.size()==limit);
    }

    private String identifier(String value,String quote) {
        if(value.isBlank())throw error(400,"INVALID_ARGUMENT","物理名称为空");
        String[] parts=value.split("\\.");List<String> safe=new ArrayList<>();
        for(String part:parts){if(!part.matches("[\\p{L}\\p{N}_$]{1,128}"))throw error(400,"INVALID_ARGUMENT","物理名称包含不允许的字符");safe.add(quote.isBlank()?part:quote+part+quote);}
        return String.join(".",safe);
    }

    private boolean sensitive(String code,String name) {
        return (code+" "+name).matches("(?i).*(身份证|证件号|手机号|电话号码|email|e.mail|phone|mobile|address|住址|银行卡|bank.card|密码|password|secret|token).*");
    }

    private boolean visible(Map<String,Object> policy,Map<String,Object> catalog) {
        String visibility=text(policy,"visibility");
        if("TENANT".equals(visibility))return true;
        if("DEPARTMENT".equals(visibility))return db.organizations().contains(text(catalog,"org_id"));
        if("AUTHORIZED_ORGS".equals(visibility))return db.organizations().stream().anyMatch(org->
                policy.get("allowedDepartmentIds") instanceof List<?> ids && ids.contains(org));
        return false;
    }

    public Object apiDebug(String apiId,Map<String,Object> input,String key) {
        queries.ref("API",apiId);
        throw error(503,"UPSTREAM_UNAVAILABLE","受控接口调试网关尚未接入");
    }

    private Map<String,Object> demandCase(String id,boolean lock) {
        db.require("da_demand_case_t","da_demand_match_t");
        var row=db.one("SELECT * FROM da_demand_case_t WHERE tenant_id=? AND apply_form_id=?"+(lock?" FOR UPDATE":""),db.tenant(),id);
        if(row==null)throw error(404,"NOT_FOUND","需求不存在");
        return row;
    }

    private void canHandle(Map<String,Object> form,Map<String,Object> demand,String action) {
        boolean handler=!text(demand,"owner_org_id").isBlank()&&db.organizations().contains(text(demand,"owner_org_id"));
        if(handler||"COMMENT".equals(action)&&db.user().equals(text(form,"apply_user_id")))return;
        throw error(403,"FORBIDDEN","没有该需求的处理权限");
    }

    private void validateCandidate(Map<String,Object> candidate,String requesterOrg) {
        String catalog=required(candidate,"catalogId",64),version=required(candidate,"versionId",64);
        var row=db.one("SELECT h.current_publication_id,p.visibility_scope,c.org_id FROM da_catalog_publication_head_t h JOIN da_catalog_publication_t p ON p.tenant_id=h.tenant_id AND p.tid=h.current_publication_id JOIN da_catalog_t c ON c.tenant_id=h.tenant_id AND c.tid=h.catalog_id WHERE h.tenant_id=? AND h.catalog_id=? AND h.availability='PUBLISHED' AND p.status='PUBLISHED'",db.tenant(),catalog);
        if(row==null||!version.equals(text(row,"current_publication_id")))throw error(409,"PUBLICATION_STALE","推荐目录已下架或版本发生变化");
        var policy=db.object(row.get("visibility_scope"));String visibility=text(policy,"visibility");
        boolean visible="TENANT".equals(visibility)||"DEPARTMENT".equals(visibility)&&requesterOrg.equals(text(row,"org_id"))||"AUTHORIZED_ORGS".equals(visibility)&&policy.get("allowedDepartmentIds") instanceof List<?> ids&&ids.contains(requesterOrg);
        if(!visible||"PROHIBITED".equals(text(policy,"shareType")))throw error(403,"FORBIDDEN","申请部门无权使用该目录");
    }

    private void append(Map<String,Object> demand,String type,String content,Map<String,Object> candidate,String key) {
        long seq=((Number)demand.get("next_event_seq")).longValue();
        String caseId=text(demand,"tid"),organization=db.organizations().stream().findFirst().orElse(text(demand,"requesting_org_id"));
        Map<String,Object> row=map("tid",id(),"tenant_id",db.tenant(),"demand_case_id",caseId,"event_seq",seq,
                "catalog_id",candidate.get("catalogId"),"publication_id",candidate.get("versionId"),"supplier_org_id",candidate.get("supplierOrgId"),
                "event_type",type,"event_message",content,"event_meta",db.encode(candidate),"idempotency_key",hash(key+":"+seq),
                "actor_user_id",db.user(),"actor_org_id",organization,"occurred_at",now(),"created_time",now());
        db.insert("da_demand_match_t",row);
        db.jdbc.update("UPDATE da_demand_case_t SET next_event_seq=next_event_seq+1 WHERE tenant_id=? AND tid=?",db.tenant(),caseId);
        demand.put("next_event_seq",seq+1);
    }
}
