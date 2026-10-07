{
  "properties": {},
  "id": "1dd6c825721a58eca0ebe748c1eacc38",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "14.授权有效期计算",
  "createTime": 1790617158595,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/time-window",
  "description": "统一解析授权起止时间与当前有效状态，供角色、权限和登录访问计算复用。",
  "parameters": [
    {
      "name": "startsAt",
      "value": null,
      "description": "授权或成员关系开始时间",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "expiresAt",
      "value": null,
      "description": "授权或成员关系截止时间",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：14.授权有效期计算
// 业务说明：统一解析授权起止时间与当前有效状态，供角色、权限和登录访问计算复用。
// 函数输入：startsAt：授权或成员关系开始时间；expiresAt：授权或成员关系截止时间。
// 返回与副作用：统一解析授权起止时间与当前有效状态，供角色、权限和登录访问计算复用。
import java.time.Instant;
import db;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

var start=null;
var end=null;
// 以数据库 UTC 当前时间作为缺省生效时间，统一解析输入的时区。
try {
    var now=db.normal().selectOne("""
select date_format(utc_timestamp(6), '%Y-%m-%dT%H:%i:%s.%fZ') now_utc
""").now_utc;
    start=startsAt==null || startsAt==''?Instant.parse(now):Instant.parse(startsAt);
    end=expiresAt==null || expiresAt==''?null:Instant.parse(expiresAt);
}
catch(e) {
    exit 400,'时间须为带时区的ISO日期';
}
// 拒绝截止时间不晚于开始时间的授权窗口。
if(end!=null && !end.isAfter(start)) {
    exit 400,'到期时间须晚于生效时间';
}
var format=DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss.SSSSSS');
return {
    start:format.format(LocalDateTime.ofInstant(start,ZoneOffset.UTC)),end:end==null?null:format.format(LocalDateTime.ofInstant(end,ZoneOffset.UTC))
};
