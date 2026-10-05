package com.linewell.dataelement.model.common;

import lombok.Getter;

/**
 * @Description: 数据资产平台统一响应编码 枚举
 * @Author: gaoZhenWen
 * @Date: 2022/7/26 17:53
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Getter
public enum StatusCodeEnum {
    SUCCESS(0, "success","成功", true),
    LOGIN_SUCCESS(0, "login_success","登录成功", true),
    LOGOUT_SUCCESS(0, "logout_success","登出成功", true),
    NOLOGIN(1, "nologin","未登录", false),
    PAGE_REDIRECT(121210, "page_redirect","页面重定向", false),
    LICENSE_ERROR(121212, "license_error","license验证失败", false),
    BASELINE_NOLOGIN(100120, "user_no_login","未登录", false),
    USER_NOT_SYNC(100200, "user_not_sync","统一用户平台账号未同步", false),

    ERROR(500, "error_500","业务异常", false),
    BAD_REQUEST(400, "bad_request","请求异常", false),
    UNAUTHORIZED(401, "unauthorized","未授权", false),
    PAYMENT(402, "payment","payment", false),
    FORBIDDEN(403, "forbidden","forbidden", false),
    NOT_FOUND(404, "not_found","请求不存在", false),

    BUSI_EXCEPTION(10086, "busi_exception","业务异常", false),
    REPEAT_SUBMIT(10089, "repeat_submit","重复提交", false),

    INVALID(-1, "invalid","不合法请求", false),
    EXCEPTION(-2, "unknow_error","未知错误", false),
    HTTP_MESSAGE_NOT_READABLE_EXCEPTION(-3, "http_message_not_readable","提交参数格式错误", false),
    HTTP_METHOD_NOT_SUPPORTED_EXCEPTION(-4, "http_method_not_supported","Http method 不支持", false),
    SERVICE_NOT_EXISTS(10001, "service_not_exists","服务不存在", false),
    SERVICE_DEVELOPING(10002, "service_developing","服务正在开发中", false),
    ENTITY_NOT_EXIST(10003, "entity_not_exist","服务实体不存在", false),
    ASSET_NOT_EXISTS(10001, "asset_not_exists","资产不存在", false),
    PARAMS_NOT_FOUND(20001, "params_not_found","参数不足", false),
    PARAMS_UNVALID(20002, "params_unvalid","参数有误", false),



    SERVICE_REQUEST_TREND_TYPE_NOT_NULL(10004, "service_request_trend_type_not_null","趋势类型不能为空", false),
    SERVICE_REQUEST_TREND_TYPE_NOT_EXISTS(10005, "service_request_trend_type_not_exists","趋势类型错误", false),
    PAGE_INDEX_NOT_ZERO(10006, "page_index_not_zero","页码不能为0", false),
    PAGE_SIZE_NOT_ZERO(10007, "page_size_not_zero","页大小不能为0", false),
    PAGE_INDEX_NOT_NULL(10008, "page_index_not_null","页码不能为空", false),
    PAGE_SIZE_NOT_NULL(10009, "page_size_not_null","页大小不能为空", false),
    SERVICE_ID_NOT_NULL(10010, "service_id_not_null","服务id不能为空", false),
    DATE_PATTERN_ERROR(10011, "date_pattern_error","日期格式错误", false),
    DATE_NOT_NULL(10012, "date_not_null","日期不能为空", false),
    Asset_Id_Not_Null(10013,"assetId_not_null","资产id不能为空",false),
    Category_Id_Not_Null(10014,"categoryId_not_null","目录id不能为空",false),
    Parent_Category_Id_Duplicate(10015,"parent_category_id_dupliacte","上级数据组织不能为自身",false),
    Type_Not_Null(10016,"type_not_null","趋势类型不能为空",false),
    Dimention_Id_Not_Null(10017,"dimention_id_not_null","评估维度id不能为空",false),
    Label_Id_Not_Null(10018,"label_id_not_null","标签id不能为空",false),
    Label_Not_EXISTS(10019,"label_id_not_exists","标签不存在",false),
    LABEL_TYPE_NAME_DUPLICATE(10019,"label_type_name_duplicate","标签维度名称重复",false),
    LABEL_TYPE_NOT_EXIST(10019,"label_type_not_exist","标签维度不存在",false),
    LABEL_SUBCLASS_NAME_DUPLICATE(10019,"label_subclass_name_duplicate","标签分类名称重复",false),
    LABEL_SUBCLASS_CODE_DUPLICATE(10019,"label_subclass_code_duplicate","标签分类编码重复",false),
    LABEL_NAME_DUPLICATE(10019,"label_name_duplicate","标签名称重复",false),
    LABEL_DIMENSION_NOT_EXIST(10019,"label_dimension_not_exist","标签维度不能为空",false),

    Status_Not_Null(10020,"status_not_null","",false),
    Asset_Class_Not_Null(10020,"asset_class_not_null","资产类型不能为空",false),
    Params_Not_Null(10021,"params_not_null","参数不能为空",false),

    VERTEX_NOT_NULL(10015,"vertex_not_null","点不能为空",false),
    VERTEX_NOT_EXITS(10016,"vertex_not_exits","点不存在",false),
    VERTEX_ID_NOT_NULL(10017,"vertex_id_not_exits","点不能为空",false),

    SUBSCRIBE_SEND_NOT_EXITS(10018,"subscribe_send_not_exits","订阅不存在",false),
    ELEMENT_CATEGORY_NOT_EXITS(10019,"element_category_not_exits","资产类型不存在",false),
    DIMENSION_NOT_EXITS(10020,"dimension_not_exits","资产维度不存在",false),
    FIELD_NOT_EXITS(10021,"field_not_exits","字段不存在",false),
    VALUATION_NOT_EXITS(10022,"valuation_not_exits","评估不存在",false),

    ORDER_NOT_UNIQUE(10023,"order_not_unique","请勿重复分派",false),

    LENGTH_TOO_LONG(10024,"length_too_long","字段长度过长，只能输入256个中文",false),

    ASSET_CLASS_NOT_SUPPORT(10025,"asset_class_not_support","当前资产类型，不支持变更",false),

    SUBSCRIBE_RECORD_NOT_EXIST(10025,"subscribe_record_not_exist","资产申请记录不存在",false),
    DUPLICATE_SUBSCRIBE(10025,"duplicate_subscribe","该资产处于订阅审批状态中,无需重复订阅",false),
    DUPLICATE_SUBMIT(10025,"duplicate_submit","该资产处于已提交审批，请勿重复提交",false),
    DUPLICATE_APPLY(10025,"duplicate_apply","当前资源目录已提交至我的申请，无需重复提交",false),
    RESOURCE_NOT_EXIST(10025,"resource_not_exist","未选择资源",false),
    ASSET_OFFLINE(10025,"asset_offline","资产已下架，无法订阅",false),
    DUPLICATE_APPROVE(10026,"duplicate_approve","请勿重复审批",false),
    ASSET_NOT_PUBLISH(10027,"asset_not_publish","资产未发布",false),
    ASSET_CLASS_NOT_SUPPORT_JOB(10028,"asset_class_not_support_job","当前资产类型，不支持查询任务",false),
    INTEGRATED_JOB_TYPE_NOT_NULL(10029,"integrated_job_type_not_null","集成任务类型不能为空",false),
    INTEGRATED_JOB_TYPE_NOT_SUPPORT(10030,"integrated_job_type_not_support","不支持集成任务类型",false),
    JOB_NOT_EXIT(10031,"job_not_exit","任务不存在",false),
    JOB_ID_NOT_NULL(10032,"job_id_not_null","任务id不能为空",false),
    DATA_SQL_NOT_NULL(10033,"data_sql_not_null","sql不能为空",false),
    DATA_SQL_NOT_SUPPORT(10034,"data_sql_not_support","不支持该类型的sql",false),
    DATA_ELEMENT_NOT_NULL(10035,"data_element_not_null","要素不存在",false),
    VERTEX_NOT_SUPPORT(10036,"vertex_not_support","不支持该类型的点插入图数据库",false),
    ELEMENT_NOT_SUPPORT(10037,"element_not_support","要素不支持，已被其他目录挂载",false),
    TABLE_NOT_EXIT(10038,"table_not_exit","表不存在",false),
    UPDATE_NOT_EXIT(10039,"update_not_exit","该升级不存在",false),
    CATEGORY_NOT_EXIT(10040,"categoryId_not_exit","目录不存在",false),
    OPERATE_NOT_SUPPORT(10041,"operate_not_support","操作不存在",false),
    CATEGORY_NOT_SUPPORT(10042,"category_not_support","目录不支持，已经挂载要素",false),
    VIRTUAL_CATEGORY_NOT_MOVE(10043,"virtual_category_not_move","虚拟目录不支持拖拽",false),
    MOVE_TO_VIRTUAL_CATEGORY_NOT_SUPPORT(10044,"move_to_virtual_category_not_support","不支持拖拽到虚拟目录",false),
    CATEGORY_SHOW_TYPE_NOT_NULL(10045,"category_show_type_not_null","目录展示类型不能为空",false),
    CATEGORY_SHOW_TYPE_NOT_EXITS(10046,"category_show_type_not_exits","资产展示类型不存在",false),
    VIRTUAL_CATEGORY_NOT_UPDATE(10047,"virtual_category_not_update","虚拟目录不支持修改",false),
    CATEGORY_ASSET_CLASS_NOT_SUPPORT(10048,"category_asset_class_not_support","目录不支持挂载资产类型",false),
    CATEGORY_NOT_SUPPORT_ASSET_CLASS_(10049,"category_asset_class_not_support","目录不支持挂载该资产类型",false),
    CATEGORY_ASSET_CLASS_NOT_UPDATE(10050,"category_asset_class_not_update","该资产类型的目录已经被编目，不支持修改",false),
    CATEGORY_ELEMENT_NOT_UPDATE(10051,"category_element_not_update","要素不支持挂载到目录上，资产类型不一致",false),
    FILE_ID_NOT_NULL(10052, "file_id_not_null","文件id不能为空", false),
    ELEMENT_ID_NOT_NULL(10053, "element_id_not_null","要素id不能为空", false),
    FILE_DOWNLOAD_ERROR(10054, "element_id_not_null","文件下载失败", false),
    FILE_SIZE_OUT_OF_RANGE(10054, "file_size_out_of_range","文件大小超出50m！", false),
    FILE_SUFFIX_ILLEGAL(10054, "file_suffix_illegal","文件类型不支持", false),

    ELEMENT_NOT_SUPPORT_CATEGORY(10055,"element_not_support_category","目录的挂载要素不能修改，该目录已经做为要素的数据",false),
    RESOURCE_IS_APPLYED(10057,"resource_is_applyed","该资产已加入申请单，请勿重复点击",false),
    RESOURCE_APPLYED(10057,"resource_is_applyed","该使用系统已经申请过该资源，请勿重复申请",false),
    MINIO_ERROR(10058,"minio","MINIO异常",false),
    VIRTUAL_CATEGORY_NOT_MOVE_OUT(10059,"virtual_category_not_move_out","虚拟目录不支持拖拽出当前目录",false),
    FIRST_CATEGORY_NOT_SUPPORT_CATALOGUE(10060,"first_category_support_catalogue","一级目录不支持编目",false),
    ELEMENT_NOT_SUPPORT_SQL(10061,"element_not_support_sql","数据录入类型为输入框的要素不支持写入sql",false),
    TABLE_ID_IS_NOT_NULL(10062,"table_id_is_not_null","表id不能为空",false),
    FIELD_NAME_IS_NOT_NULL(10063,"field_name_is_not_null","字段名称不能为空",false),
    CATEGORY_NOT_MOUNT_TABLE(10064,"category_not_mount_table","资源目录没有挂载表",false),
    CATEGORY_NOT_HAVE_TABLE(10065,"category_not_have_table","资源目录没有这张表",false),
    CATEGORY_NOT_SHARE(10065,"category_not_share","不予共享的资产不能进行申请",false),
    FIELD_RELATION_NOT_EXIT(10066,"field_relation_not_exit","字段关联关系不存在",false),
    APP_ID_AND_CATEGORY_ID_NOT_ALL_EXIT(10067,"app_id_and_category_id_not_all_exit","应用id和目录id不能同时存在",false),
    APP_NOT_HAVE_TABLE(10068,"app_not_have_table","应用系统下不存在表",false),
    TABLE_NOT_BELONG_TO_APP(10069,"table_not_belong_to_app","表不属于该应用",false),
    APP_ID_AND_CATEGORY_ID_NOT_ALL_NULL(10070,"app_id_and_category_id_not_all_null","应用id和目录id不能都为空",false),
    CATEGORY_ITEM_NOT_EXIT(10071,"category_item_not_exit","资源目录信息项不存在",false),
    CATEGORY_ITEM_NOT_BELONG_TO_ONE_CATEGORY(10072,"category_item_not_belong_to_one_category","信息项不属于同一个资源目录",false),
    ITEM_SOCIETY_OPEN_VALUE_NOT_EXIT(10073,"item_society_open_value_not_exit","是否社会开放值不存在",false),
    DESENSITIZATION_RULE_NOT_EXIT(10074,"desensitization_rule_not_exit","脱敏规则不存在",false),
    DESENSITIZATION_RULE_ID_NOT_NULL(10075,"desensitization_rule_id_not_null","脱敏规则id不能为空",false),
    CATEGORY_NOT_MOUNT_ITEM(10076,"category_not_mount_item","资源目录没有信息项",false),
    TARGET_TABLE_ITEM_IS_NULL(10077,"target_table_item_is_null","目标表没有字段，无法进行手工录入",false),
    TARGET_TABLE_ITEM_NOT_CONTAIN_ITEM(10078,"target_table_item_not_contain_item","目标表存在不包含信息项的字段，无法进行手工录入",false),
    ITEM_NOT_EXIT(10079,"item_not_exit","信息项不存在",false),
    ASSETS_NOT_EXIT_TABLE(10080,"assets_not_exit_table","资产中不存在这张表",false),
    IS_CHECK_ERROR_DATA_VALUE_ERROR(10081,"is_check_error_data_value_error","是否校验错误数据值错误",false),
    ACCESS_NETWORK_IS_NOT_EXIT(10082,"access_network_is_not_exit","运行网络不存在",false),
    ACCESS_NETWORK_VALUE_ERROR(10083,"access_network_value_error","运行网络值错误",false),
    TASK_MOLD_ERROR(10084,"task_mold_error","接入类型错误",false),
    MANUAL_ENTRY_TEMP_NOT_EXIT(10085,"manual_entry_temp_not_exit","人工录入草稿不存在",false),
    MANUAL_ENTRY_TEMP_CATEGORY_NOT_AGREEMENT(10086,"manual_entry_temp_category_not_agreement","人工录入草稿的目录不一致",false),
    APP_NOT_HAVE_SRC_DEPART(10087,"app_not_have_src_depart","业务系统没有来源部门",false),
    DB_NOT_HAVE_APP(10088,"db_not_have_app","库没有业务系统",false),
    ASSET_NOT_ESCALATION(10089,"asset_not_escalation","资产未上报",false),
    ASSET_ESCALATION_ING(10090,"asset_escalation_ing","资产已经提交审核",false),
    ASSET_NOT_ESCALATION_EXAMINE(10091,"asset_not_escalation_examine","资产未提交审核",false),
    MANUAL_ENTRY_STATUS_ERROR(10092,"manual_entry_status_error","只有草稿状态可以提交",false),
    CREATE_JOB_ERROR(10093,"create_job_error","创建任务失败",false),
    FIELD_IS_MAP(10094,"field_is_map","信息项已经映射",false),
    CANT_DISPATCH(10084,"can_not_dispatch","存在不能分派的需求单",false),
    DEMAND_NOT_EXIST(10084,"demand_not_exist","需求单不存在",false),
    ORG_NOT_FOUND(10084,"org_not_exist","未找到当前用户所属部门",false),

    NOT_MOUNT_TABLE(10185,"not_mount_table","未挂载数据表",false),
    FIELD_NOT_MAP(10186,"field_not_map","未映射数据项",false),
    CATALOG_TABLE_NOT_EXIST(10187,"catalog_table_not_exist","catalog表不存在该资产",false),
    KAFKA_CONNECT_FAILED(10188,"kafka_connect_failed","kafka连接失败",false),
    SUPPORT_HTTP_SERVICE(10190,"support_http_service","只支持http类型的接口进行上报",false),
    MAPPING_TABLE_NOT_EXIT(10191,"mapping_table_not_exit","选择的表在中心不存在，不能创建任务",false),
    FIle_IS_EXITS(10092,"file_is_exits","文件已存在",false),
    FIle_NOT_MOUNT(10093,"file_not_mount","文件未挂载",false),
    FIle_IS_MOUNT(10094,"file_is_mount","文件已经挂载",false),
    API_IS_MOUNT(10095,"api_is_mount","服务已经挂载",false),
    API_NOT_MOUNT(10096,"api_not_mount","服务未挂载",false),
    TABLE_NOT_HAVE_FIELD(10097,"table_not_have_field","挂载的表没有字段",false),
    IMPORT_ROW_TOO_MANY(10098,"import_data_too_many","导入的数据不能超过5000行",false),
    FILE_DATA_IS_EMPTY(10099,"file_data_is_empty","表格数据不能为空",false),
    REQUIRED_NOT_NULL(10100,"required_not_null","必填不能为空",false),
    PK_NOT_NULL(10101,"pk_not_null","主键值不能为空",false),
    PK_IS_EXIT(10102,"pk_is_exit","主键值重复",false),
    FILE_PK_IS_EXIT(10103,"file_pk_is_exit","文件中主键值重复",false),



    ASSET_AUTH_NONE(20002, "asset_auth_none","所有资产均已授权，无需重新授权", false),
    ASSET_PUBLISH_ERROR(20003, "asset_publish_error", "发布失败", false),
    ASSET_OFFLINE_ERROR(20004, "asset_offline_error", "下架失败", false),
    ASSET_UPDATE_ERROR(20005, "asset_update_error", "ES更新数据失败", false),


    OPERATION_SUCCEEDED(0, "operation_succeeded","操作成功", true),
    OPERATION_FAILED(10082, "operation_failed","操作失败", false),
    EXIST_OTHER_ELEMENT(10082, "operation_failed","存在其他资产要素", false),
    CODE_NOT_UNIQUE(10082, "编码重复","编码重复", false),
    CHANNEL_CODE_NOT_UNIQUE(10082, "code_not_unique","渠道ID重复", false),
    ELEMENT_NAME_NOT_UNIQUE(10082, "code_not_unique","当前提交名称重复", false),
    NAME_NOT_UNIQUE(10082, "name_not_unique","同一级目录下，目录名称重复", false),
    VALUE_ALREADY_EXIST(10083, "value_already_exist","该值已存在，请重新输入", false),
    HTTP_SERVER_REEOR(-4,"hhtp_server_error","请求第三方服务失败",false),

    IS_EXIST_ASSERT(5000,"is_exist_asset","已存在相同资产",false),
    IS_EXIST_DB_ASSERT(5001,"is_exist_db_asset","已存在相同库资产",false),
    IS_EXIST_SERVICE_ASSERT(5002,"is_exist_service_asset","已存在相同服务资产",false),
    IS_EXIST_NAME(-6,"当前名称已登记","当前名称已登记",false),
    DUPLICATE_NAME(-6,"当前内容资产名有重复","当前内容资产名有重复",false),
    CONNECT_FAIL(-7,"建立连接失败","建立连接失败",false),

    APP_UNVALID(30001, "app_unvalid", "应用ID或密钥不正确", false),
    APP_FORBIDDEN(30002, "app_forbidden", "应用已禁用", false),
    APP_AUTH_TIMEOUT(30002, "app_auth_timeout", "token已失效", false),
    APP_AUTH_TOKEN_ERROR(30002, "app_auth_token_error", "token无效", false),
    APP_AUTH_TOKEN_ERROR_A(30002, "app_auth_token_error", "token无效", false),

    CLASS_NAME_NOT_FOUND(40001,"class_name_not_found","语法不正确:未查询到Public类名",false),
    FILE_PATH_NOT_FOUND(40001,"file_path_not_found","未查询到文件存放路径",false),
    SCRIPT_ERROR(40001,"script_error","脚本运行错误",false),

    DUPLICATE_APPLICATION_NAME(-6,"duplicate_application_name","应用名称重复",false),
    //对接API标准
    REQ_SUCCESS(0, "req_success", "请求成功", true),
    REQ_ERROR(0, "req_error", "请求失败", true),
    REQ_ACTION_IS_NULL(50001, "req_action_is_null", "action为空", false),
    REQ_APPKEY_IS_NULL(50002, "req_appkey_is_null", "appkey为空", false),
    REQ_APPKEY_NOT_MATCH(50003, "req_appkey_not_match", "appkey错误", false),
    REQ_CLIENTID_IS_NULL(50004, "req_clientid_is_null", "clentId为空", false),
    REQ_CLIENTID_NOT_MATCH(50005, "req_clientid_not_match", "应用不存在", false),
    REQ_TIMESTAMP_IS_NULL(50006, "req_timestamp_is_null", "timestamp为空", false),
    REQ_TIMESTAMP_NOT_MATCH(50007, "req_timestamp_not_match", "timestamp与当前时间不符", false),
    REQ_TOKEN_IS_NULL(50008, "req_token_not_match", "accessToken为空", false),
    REQ_TOKEN_NOT_MATCH(50009, "req_token_not_match", "accessToken不存在或已过期", false),
    REQ_SIGN_IS_NULL(50010, "req_sign_is_null", "sign为空", false),
    REQ_SIGN_NOT_MATCH(50011, "req_sign_not_match", "sign错误", false),
    REQ_API_NOT_EXIST(50012, "req_api_not_exist", "接口不存在", false),

    REQ_API_ASSET_LEVEL_NOT_MATCH(50013, "req_api_asset_level_not_match", "资产分级不存在", false),
    REQ_API_UPDATE_CIRCLE_NOT_MATCH(50014, "req_api_update_circle_not_match", "更新周期不存在", false),
    REQ_API_ENVIRONMENT_NOT_MATCH(50015, "req_api_environment_not_match", "库资产所属环境不存在", false),
    REQ_API_STORAGENET_NOT_MATCH(50016, "req_api_storagenet_not_match", "存储网域不存在", false),
    REQ_API_INDUSTRYTYPE_NOT_MATCH(50017, "req_api_industrytype_not_match", "行业类别不存在", false),
    REQ_API_DBTYPE_NOT_MATCH(50018, "req_api_dbtype_not_match", "数据库类型不存在", false),
    REQ_VERSION_NOT_MATCH(50019, "req_version_not_match", "版本号不匹配", false),
    REQ_PARAM_NOT_EXIST(50020, "req_param_not_exist", "参数不足", false),
    REQ_DICTIONARY_NOT_EXIST(50021, "req_dictionary_not_exist", "字典数据不存在", false),
    REQ_DB_CONNECT_ERROR(50022, "req_db_connect_error", "数据库无法连接", false),
    REQ_DB_ASSET_NOT_EXIST(50023, "req_db_asset_not_exist", "库资产不存在", false),
    REQ_TABLE_NOT_EXIST(50024, "req_table_not_exist", "表不存在", false),
    REQ_ASSET_ALREADY_EXIST(50024, "req_asset_already_exist", "资产已存在", false),

    CENTER_NOT_EXIST(50025, "center_not_exist", "数据中心不存在该资产",false),

    DELETE_FAIL(50026,"delete_fail","已准入或审批中的资产不可删除",false),
    CATALOG_NOT_EXIST(50025, "catalog_not_exist", "请先配置trino数据源", false),


    CRON_INVALID(50025, "crom_invalid", "cron表达式有误", false),



    ORGA_HAS_RELA_USERS(101147,"orga_has_rela_users","存在用户关联该组织，无法删除！",false),
    ROLE_HAS_RELA_USERS(101147,"role_has_rela_users","存在用户关联该角色，无法删除！",false),
    PAGE_COMP_CODE_HAS_EXIST(101146,"page_comp_code_has_exist","该页面组件编码已经存在，请重新输入",false),
    ROLE_CODE_HAS_EXIST(101146,"role_code_has_exist","该角色编码已经存在，请重新输入",false),
    MENU_NOT_EXIST(101146,"menu_not_exist","菜单不存在",false),
    MENU_HAS_CHILD(101146,"menu_has_child","存在子菜单，无法删除！",false),
    MENU_NAME_DUPLICATE(101146,"menu_name_duplicate","菜单名称重复",false),
    USER_ACCOUNT_DUPLICATE(101146,"user_account_duplicate","用户账号名称重复",false),
    OVERSTEP_AUTHORITY_CHECK_DATA(60025, "overstep_authority_check_data", "涉及越权操作数据！", false),
    ;

    private Integer code;
    private String msg;
    private String desc;
    private Boolean success;

    StatusCodeEnum(Integer code, String msg, String desc, Boolean success) {
        this.code = code;
        this.msg = msg;
        this.desc = desc;
        this.success = success;
    }

    public static StatusCodeEnum get(Integer code) {
        StatusCodeEnum[] arr = StatusCodeEnum.values();
        for (StatusCodeEnum s : arr) {
            if (s.getCode().equals(code)) {
                return s;
            }
        }
        return null;
    }
}
