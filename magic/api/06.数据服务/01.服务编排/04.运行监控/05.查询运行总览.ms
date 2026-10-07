{
  "properties": {},
  "id": "e76f4152440c795b286040a5ba3de0c6",
  "script": null,
  "groupId": "252018678f71bd2962b4ed7e5c2b6b56",
  "name": "05.查询运行总览",
  "createTime": 1784826968202,
  "updateTime": 1784826968202,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/monitor/overview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "统计服务、调用、数据源、近十二小时趋势和错误排行。示例：GET /dws/flowserve/monitor/overview。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.查询运行总览
// 路由：GET /dws/flowserve/monitor/overview
// 业务说明：统计服务、调用、数据源、近十二小时趋势和错误排行。示例：GET /dws/flowserve/monitor/overview。
// 查询运行总览：处理 GET /monitor/overview 的核心查询和组装返回数据逻辑。
var since12Hours = new Date(new Date().getTime() - 12 * 60 * 60 * 1000)
var since24Hours = new Date(new Date().getTime() - 24 * 60 * 60 * 1000)
var serviceCount = db.selectInt("""
select count(*)
from fs_flow
where status='published'
    and deleted=0
""")
var todayCalls = db.selectInt("""
select count(*)
from fs_call_log
where created_at>=curdate()
""")
var activeDataSources = db.selectInt("""
select count(*)
from fs_datasource
""")
var series = db.select("""
select date_format(created_at, '%Y-%m-%d %H:00:00') ts, count(*) qps,coalesce(avg(duration_ms),
        0) p95,coalesce(avg(success),
        1) success,
       sum(case when success=0 then 1 else 0
           end) error
from fs_call_log
where created_at>=#{since12Hours}
group by date_format(created_at, '%Y-%m-%d %H:00:00')
order by ts
""")
var errorTop = db.select("""
select coalesce(service_name, path) name,count(*) errors
from fs_call_log
where success=0
    and created_at>=#{since24Hours}
group by coalesce(service_name, path)
order by errors desc
limit 10
""")
return {serviceCount:serviceCount,todayCalls:todayCalls,
  activeDataSources:activeDataSources,errorTop:errorTop,series:series}
