{
  "properties": {},
  "id": "b36e38effc614a97b017eebae79eba13",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "08.获取最近5次Cron执行时间",
  "createTime": 1782955344767,
  "updateTime": 1784437771802,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/lastFiveCron",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"cron\": \"5 4 * * * *\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"无效的 Cron 表达式: 5 4 * * * *2 s\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "导入 Spring 提供的 Cron 表达式工具类；使用请求参数 cron；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.获取最近5次Cron执行时间
// 路由：POST /ods/task/lastFiveCron
// 业务说明：导入 Spring 提供的 Cron 表达式工具类；使用请求参数 cron；读取或计算后返回业务结果。
// 导入 Spring 提供的 Cron 表达式工具类
import org.springframework.scheduling.support.CronExpression
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// 获取参数中的 cron 表达式
var cron = body.cron 

// 1. 校验 Cron 表达式是否有效
if (!CronExpression.isValidExpression(cron)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "无效的 Cron 表达式: " + cron
}

// 2. 解析表达式
var expression = CronExpression.parse(cron)
var result = []
var nextTime = ZonedDateTime.now()

// 3. 循环计算最近 5 次执行时间
for ( i in range(0,4)) {
    nextTime = expression.next(nextTime)
    if (nextTime == null) {
      break
    }
    result.add(nextTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
}

// 返回当前业务分支的标准结果。
return  result
