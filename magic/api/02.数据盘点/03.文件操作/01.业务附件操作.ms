{
  "properties": {},
  "id": "6a8a78b375224683a6afbcdac223aef6",
  "script": null,
  "groupId": "e79210b931bf5e1e9070de78883499c9",
  "name": "01.业务附件操作",
  "createTime": 1782660730213,
  "updateTime": 1786951858953,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/manage",
  "method": "POST",
  "parameters": [
    {
      "name": "bucket",
      "value": "测试",
      "description": "桶名称",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "targetBucket",
      "value": null,
      "description": "真实数据示例参数：targetBucket",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "fileNames",
      "value": "测试",
      "description": "文件名称",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "action",
      "value": "download",
      "description": "upload 上传通用附件，upload-report 上传数据上报原件，download 下载，delete 删除通用附件",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "file",
      "value": {},
      "description": "真实数据示例参数：file",
      "required": false,
      "dataType": "MultipartFile",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tid",
      "value": "2e83a7abf33140c7a3df27d0c79e98c1",
      "description": "真实数据示例参数：tid",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"fileId不正确\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "按当前租户上传、删除或流式读取业务附件。数据上报原件上传校验登录、格式和大小，完成磁盘写入及文件登记后返回标识和下载地址。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "500",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "fileId不正确",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
import tenantRuntime
import com.linewell.dataelement.utils.MinioUtils
import cn.hutool.core.util.IdUtil
import log
import '@/common/configData' as configData
import '@/common/reportoriginal' as reportOriginal
import systemConfig
import response;
import cn.hutool.core.util.StrUtil;
import cn.dev33.satoken.stp.StpUtil
import java.net.URLEncoder

// POST /dst/file/manage 按当前租户上传、删除或流式读取业务附件。上传接收 multipart 文件，返回附件标识和下载地址。
// upload-report 校验上传者与原件类型，通过 /common/reportoriginal 写磁盘；登记失败时清除此份原件。

var tenantId = tenantRuntime.id()
if (action == 'upload-report') {
    StpUtil.checkLogin()
    if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '请重新登录后办理数据上报' }
    if (file == null || file.isEmpty() || file.getSize() > 10485760) {
        exit 400, '数据上报原件大小须在 1 字节至 10 MB 之间'
    }
    var fileName = file.getOriginalFilename()
    if (fileName == null || fileName.length() > 255 || fileName.contains('/') || fileName.contains('\\')
        || !fileName.matches('(?i)[^\\p{Cntrl}]+\\.(csv|xlsx|xls|txt|json)')) {
        exit 400, '仅支持文件名有效的 CSV、Excel、TXT、JSON 数据文件'
    }
    var actorId = StpUtil.getLoginIdAsString()
    var originalId = IdUtil.fastSimpleUUID()
    var storageKey = 'report-local:' + originalId
    try {
        reportOriginal('write', tenantId, storageKey, file)
    } catch (e) {
        log.warn('上报原件落盘失败，原件标识 {}', originalId)
        exit 500, '上传原件保存失败，请重试'
    }
    var now = new Date()
    try {
        var inserted = db.table('file_upload_t').primary('tid').insert({
            tid: originalId, file_path: storageKey, file_name: fileName,
            file_size: file.getSize(), file_suffix: fileName.substring(fileName.lastIndexOf('.') + 1),
            tenant_id: tenantId, created_by: actorId, updated_by: actorId,
            created_time: now, updated_time: now, is_del: 0
        })
        if (inserted == 0) { throw new IllegalStateException('无法登记上传原件') }
    } catch (e) {
        // 插入结果可能因连接中断未返回。重新核对记录后再清理，避免删除已提交记录的原件。
        var confirmed = null
        try {
            confirmed = db.table('file_upload_t').where().eq('tid', originalId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
            if (confirmed == null) { reportOriginal('discard', tenantId, storageKey, null) }
        } catch (cleanupError) { log.warn('无法确认上报原件登记或清理状态，原件标识 {}', originalId) }
        if (confirmed == null || confirmed.filePath != storageKey) { exit 500, '上传原件登记失败，请重试' }
    }
    var downloadUrl = `/dst/file/download?fileId=${originalId}`
    return { tid: originalId, name: fileName, url: downloadUrl, preView: downloadUrl }
}

var existing = null
if (action == 'download' || action == 'delete') {
    var id = action == 'download' ? fileId : tid
    existing = db.table('file_upload_t').where().eq('tid', id).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (existing == null) { exit 500, 'fileId不正确' }
    if (existing.filePath != null && existing.filePath.startsWith('report-local:')) {
        StpUtil.checkLogin()
        if (action == 'delete') { exit 400, '请在数据上报登记清单中管理原件' }
        var stream = null
        try { stream = reportOriginal('open', tenantId, existing.filePath, null) }
        catch (e) { exit 404, '上传原件不可读取' }
        try {
            var filename = URLEncoder.encode(existing.fileName, 'UTF-8').replace('+', '%20')
            response.setHeader('Content-Type', 'application/octet-stream')
            response.setHeader('Content-Disposition', "attachment; filename*=UTF-8''" + filename)
            stream.transferTo(response.getOutputStream())
        } finally { stream.close() }
        return response.end()
    }
}
log.info("bucket:{}",bucket)
if(bucket == null || bucket == ""){
  bucket = bucket || "attachment"
}

// ========== 上传到 Minio ==========
MinioUtils.getMinioClient(configData().minio_url, configData().minio_user, systemConfig.legacyMinioPassword());

if(action == 'upload'){
    createdBy = ""
    try{
        createdBy = StpUtil.getLoginId() + ""
    }catch(e) {
        createdBy = null
    }

    // ========== 获取文件元信息 ==========
    var fileName   = file.getOriginalFilename()
    var fileSize   = file.getSize()
    var fileSuffix = ''
    if (fileName != null && fileName.contains('.')) {
        fileSuffix = fileName.substring(fileName.lastIndexOf('.') + 1)
    }


    // 上传单个文件
    var tid = IdUtil.getSnowflakeNextIdStr()
    var now = new Date()

    MinioUtils.uploadFile(bucket, tid + "." + fileSuffix, file)

    var filePath = bucket + "/" + tid + "." + fileSuffix;

    log.info('文件上传Minio成功, bucket: {}, fileName: {}, filePath: {}', bucket, fileName, filePath)

    // ========== 保存上传记录 file_upload_t ==========
    db.table('file_upload_t').primary('tid').insert({
        tid:          tid,
        file_id:      fileId,
        file_path:    filePath,
        file_name:    fileName,
        file_size:    fileSize,
        file_suffix:  fileSuffix,
        tenant_id:    tenantId,
        created_by:   createdBy,
        created_time: now,
        updated_by:   createdBy,
        updated_time: now,
        is_del:       0
    })
    log.info('保存文件上传记录成功, tid: {}, fileName: {}, fileSize: {}', tid, fileName, fileSize)

    // 返回当前业务分支的标准结果。
    return {
        tid:       tid,
        name:      fileName,
        url:       `/dst/file/download?fileId=${tid}`,
        preView:   `/${filePath}`
    }
}
else if(action == 'delete'){
    var relaName = StrUtil.subAfter(existing.filePath, "/", false);
    // 删除单个文件
    return MinioUtils.deleteFile(bucket, relaName);
}
else if(action == 'download'){
    // 下载单个文件
    relaName = StrUtil.subAfter(existing.filePath, "/", false);
    bucket =  StrUtil.subBefore(existing.filePath, "/", false);
    response.setHeader("Content-disposition", "attachment; filename="+ existing.fileName);
    MinioUtils.downloadFile(bucket, relaName, response.getOutputStream());
}
