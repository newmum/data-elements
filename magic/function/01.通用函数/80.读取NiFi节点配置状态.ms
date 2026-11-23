{
  "id": "nifi_node_configuration_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "80.读取NiFi节点配置状态",
  "path": "/nifinodeconfig",
  "description": "节点选项、物化建表、任务创建和运行节点读取共用的配置检查。只处理调用方已按租户授权读取的节点，不查询数据库、不连接NiFi、不返回凭据。",
  "parameters": [{"name": "node", "dataType": "Object", "required": true}]
}
================================
import java.net.URI

// @/common/nifinodeconfig 供节点选项、物化建表、任务创建及运行配置读取共用。
// 调用方先按当前租户和有效记录预取节点；本函数仅返回可公开的状态，不传出用户名、密码或token。
var text = value => value == null ? '' : (value + '').trim()
var field = (camel, snake) => node[camel] == null ? node[snake] : node[camel]
var nodeId = text(node.tid == null ? node.id : node.tid)
var code = text(field('nodeCode', 'node_code'))
var name = text(field('nodeName', 'node_name'))
var address = text(field('baseUrl', 'base_url')).replaceAll('/+$', '')
var usernamePresent = text(field('authUsername', 'auth_username')) != ''
var passwordPresent = text(field('authPassword', 'auth_password')) != ''
var issues = []
var publicAddress = ''
var protocol = ''
if (code == '') { issues.add('缺少节点编码') }
if (address == '') {
    issues.add('缺少服务地址')
} else {
    try {
        var uri = new URI(address)
        protocol = text(uri.getScheme())
        var host = text(uri.getHost())
        if (host != '' && ['http', 'https'].contains(protocol)) {
            publicAddress = protocol + '://' + host + (uri.getPort() < 0 ? '' : ':' + uri.getPort()) + text(uri.getRawPath())
        }
        if (publicAddress == '') { issues.add('服务地址须为有效的HTTP或HTTPS地址') }
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            issues.add('服务地址不能包含账号密码、查询参数或页面片段')
        }
    } catch (invalid) {
        issues.add('服务地址格式不正确')
    }
}
var anonymous = protocol == 'http' && !usernamePresent && !passwordPresent
if (!anonymous) {
    if (!usernamePresent) { issues.add('缺少NiFi用户名') }
    if (!passwordPresent) { issues.add('缺少NiFi密码') }
}
var label = name != '' ? name : (code != '' ? code : nodeId)
var location = publicAddress == '' ? '地址未配置或格式无效' : publicAddress
var message = issues.isEmpty() ? '' : '数据接入节点「' + label + '」（' + location + '，ID：' + nodeId + '）：' + issues.join('；')
if (!issues.isEmpty() && protocol == 'http' && (usernamePresent || passwordPresent)) {
    message += '；HTTP免认证节点须同时留空用户名和密码'
}
return {configurationValid: issues.isEmpty(), configurationMessage: message, configurationIssues: issues,
    authenticationMode: anonymous ? 'ANONYMOUS_HTTP' : 'USERNAME_PASSWORD', baseUrl: publicAddress}
