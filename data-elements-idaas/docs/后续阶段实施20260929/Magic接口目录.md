# 统一身份管理平台 Magic 接口目录

本目录由实际发布清单生成。编辑器：`http://localhost:8088/api/web/index.html`；分类：**13.统一身份管理**。业务规则、SQL、幂等、审计和状态流转均在相应 Magic 源文件。修改已发布资源后刷新即可生效；修改工程源码应使用增量发布脚本，不能重新运行历史安装器。

当前清单：120 个 HTTP 业务接口、51 个共享业务函数、32 个接口分组。定时工作者及其自动同步资源已于 2026-10-01 退役，见 [手动同步实施记录](../同步改为手动执行20261001.md)。应用顺序另由手动排序接口维护。

OAuth/OIDC的授权、令牌、撤销、公钥和发现端点由Java协议框架处理；其客户端、账号、授权和策略决策通过可信桥接调用Magic函数。接收接口选择数据库的方式是部署固定路由，不能由下发报文自行指定租户。

## 01.认证会话

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.平台登录 | `POST /idaas/auth/login` | [auth-login](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-login.ms) |
| 02.退出平台 | `POST /idaas/auth/logout` | [auth-logout](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-logout.ms) |
| 03.登录品牌 | `GET /idaas/auth/public-settings` | [auth-public-settings](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-public-settings.ms) |

## 02.当前会话

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.当前操作账号 | `GET /idaas/session/me` | [session-me](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/session-me.ms) |
| 02.domain | `POST /idaas/session/domain` | [session-domain](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/session-domain.ms) |

## 03.工作区

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.平台工作区 | `GET /idaas/workspace/bootstrap` | [workspace-bootstrap](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/workspace-bootstrap.ms) |

## 04.应用管理

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取列表 | `GET /idaas/applications/list` | [applications-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/applications-list.ms) |
| 02.保存对象 | `POST /idaas/applications/save` | [applications-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/applications-save.ms) |
| 03.调整应用顺序 | `POST /idaas/applications/reorder` | [applications-reorder](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/applications-reorder.ms) |
| 03.归档对象 | `POST /idaas/applications/remove` | [applications-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/applications-remove.ms) |
| 04.核验运行绑定 | `POST /idaas/applications/runtime-binding` | [applications-runtime-binding](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/applications-runtime-binding.ms) |
| 05.runtime-inspection | `GET /idaas/applications/runtime-inspection` | [applications-runtime-inspection](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/applications-runtime-inspection.ms) |

## 05.应用分组

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取列表 | `GET /idaas/application-groups/list` | [application-groups-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/application-groups-list.ms) |
| 02.保存对象 | `POST /idaas/application-groups/save` | [application-groups-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/application-groups-save.ms) |
| 03.归档对象 | `POST /idaas/application-groups/remove` | [application-groups-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/application-groups-remove.ms) |

## 06.平台账号

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取列表 | `GET /idaas/operators/list` | [operators-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/operators-list.ms) |
| 02.保存对象 | `POST /idaas/operators/save` | [operators-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/operators-save.ms) |
| 03.归档对象 | `POST /idaas/operators/remove` | [operators-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/operators-remove.ms) |
| 04.重置平台密码 | `POST /idaas/operators/reset-password` | [operators-reset-password](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/operators-reset-password.ms) |
| 05.解除平台锁定 | `POST /idaas/operators/unlock` | [operators-unlock](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/operators-unlock.ms) |

## 07.平台角色

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取列表 | `GET /idaas/platform-roles/list` | [platform-roles-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/platform-roles-list.ms) |
| 02.保存对象 | `POST /idaas/platform-roles/save` | [platform-roles-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/platform-roles-save.ms) |
| 03.归档对象 | `POST /idaas/platform-roles/remove` | [platform-roles-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/platform-roles-remove.ms) |
| 04.平台功能资源 | `GET /idaas/platform-roles/resources` | [platform-roles-resources](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/platform-roles-resources.ms) |
| 05.当前角色分配 | `GET /idaas/platform-roles/assignments` | [platform-roles-assignments](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/platform-roles-assignments.ms) |
| 06.分配角色和范围 | `POST /idaas/platform-roles/assign` | [platform-roles-assign](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/platform-roles-assign.ms) |

## 08.平台配置

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取已保存策略 | `GET /idaas/settings/list` | [settings-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/settings-list.ms) |
| 02.保存平台策略 | `POST /idaas/settings/save` | [settings-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/settings-save.ms) |

## 09.个人中心

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.修改本人资料 | `POST /idaas/profile/save` | [profile-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/profile-save.ms) |
| 02.修改本人密码 | `POST /idaas/profile/password` | [profile-password](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/profile-password.ms) |

## 10.审计查询

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.分页审计查询 | `GET /idaas/audit/list` | [audit-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/audit-list.ms) |

## 11.人员目录

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取现有中央目录 | `GET /idaas/users/list` | [users-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/users-list.ms) |
| 02.目录升级状态 | `POST /idaas/users/save` | [users-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/users-save.ms) |
| 03.目录升级状态 | `POST /idaas/users/remove` | [users-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/users-remove.ms) |
| 04.目录升级状态 | `GET /idaas/users/operations` | [users-operations](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/users-operations.ms) |
| 05.目录升级状态 | `POST /idaas/users/retry` | [users-retry](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/users-retry.ms) |

## 12.机构目录

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.读取现有中央目录 | `GET /idaas/orgs/list` | [orgs-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/orgs-list.ms) |
| 02.目录升级状态 | `POST /idaas/orgs/save` | [orgs-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/orgs-save.ms) |
| 03.目录升级状态 | `POST /idaas/orgs/remove` | [orgs-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/orgs-remove.ms) |
| 04.目录升级状态 | `GET /idaas/orgs/operations` | [orgs-operations](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/orgs-operations.ms) |
| 05.目录升级状态 | `POST /idaas/orgs/retry` | [orgs-retry](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/orgs-retry.ms) |

## 13.标准下发

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.下发接入状态 | `GET /idaas/provision/options` | [provision-options](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-options.ms) |
| 02.下发接入状态 | `GET /idaas/provision/configs` | [provision-configs](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-configs.ms) |
| 03.下发接入状态 | `GET /idaas/provision/tasks` | [provision-tasks](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-tasks.ms) |
| 04.下发接入状态 | `POST /idaas/provision/save-config` | [provision-save-config](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-save-config.ms) |
| 05.下发接入状态 | `POST /idaas/provision/test` | [provision-test](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-test.ms) |
| 06.下发接入状态 | `POST /idaas/provision/create` | [provision-create](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-create.ms) |
| 07.下发接入状态 | `POST /idaas/provision/execute` | [provision-execute](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-execute.ms) |
| 08.rotate | `POST /idaas/provision/rotate` | [provision-rotate](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-rotate.ms) |
| 09.cancel | `POST /idaas/provision/cancel` | [provision-cancel](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-cancel.ms) |
| 10.retry | `POST /idaas/provision/retry` | [provision-retry](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/provision-retry.ms) |

## 14.subjects

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.查询中央人员 | `GET /idaas/subjects/list` | [subjects-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/subjects-list.ms) |
| 02.保存中央人员 | `POST /idaas/subjects/save` | [subjects-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/subjects-save.ms) |
| 03.归档中央人员 | `POST /idaas/subjects/remove` | [subjects-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/subjects-remove.ms) |

## 15.dictionaries

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.目录配置list | `GET /idaas/dictionaries/list` | [dictionaries-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/dictionaries-list.ms) |
| 02.目录配置save | `POST /idaas/dictionaries/save` | [dictionaries-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/dictionaries-save.ms) |
| 03.目录配置remove | `POST /idaas/dictionaries/remove` | [dictionaries-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/dictionaries-remove.ms) |

## 16.field-definitions

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.目录配置list | `GET /idaas/field-definitions/list` | [field-definitions-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/field-definitions-list.ms) |
| 02.目录配置save | `POST /idaas/field-definitions/save` | [field-definitions-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/field-definitions-save.ms) |
| 03.目录配置remove | `POST /idaas/field-definitions/remove` | [field-definitions-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/field-definitions-remove.ms) |

## 17.assignments

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.应用资料分配list | `GET /idaas/assignments/list` | [assignments-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/assignments-list.ms) |
| 02.应用资料分配save | `POST /idaas/assignments/save` | [assignments-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/assignments-save.ms) |
| 03.应用资料分配remove | `POST /idaas/assignments/remove` | [assignments-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/assignments-remove.ms) |

## 18.appointments

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.人员任职list | `GET /idaas/appointments/list` | [appointments-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/appointments-list.ms) |
| 02.人员任职save | `POST /idaas/appointments/save` | [appointments-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/appointments-save.ms) |

## 19.roles

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/roles/list` | [roles-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/roles-list.ms) |
| 02.save | `POST /idaas/roles/save` | [roles-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/roles-save.ms) |
| 03.remove | `POST /idaas/roles/remove` | [roles-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/roles-remove.ms) |

## 20.resources

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/resources/list` | [resources-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/resources-list.ms) |
| 02.save | `POST /idaas/resources/save` | [resources-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/resources-save.ms) |
| 03.remove | `POST /idaas/resources/remove` | [resources-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/resources-remove.ms) |

## 21.grants

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/grants/list` | [grants-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/grants-list.ms) |
| 02.save | `POST /idaas/grants/save` | [grants-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/grants-save.ms) |
| 03.remove | `POST /idaas/grants/remove` | [grants-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/grants-remove.ms) |
| 04.explain | `GET /idaas/grants/explain` | [grants-explain](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/grants-explain.ms) |

## 22.permission-groups

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/permission-groups/list` | [permission-groups-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/permission-groups-list.ms) |
| 02.save | `POST /idaas/permission-groups/save` | [permission-groups-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/permission-groups-save.ms) |
| 03.remove | `POST /idaas/permission-groups/remove` | [permission-groups-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/permission-groups-remove.ms) |

## 23.clients

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/clients/list` | [clients-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/clients-list.ms) |
| 02.save | `POST /idaas/clients/save` | [clients-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/clients-save.ms) |
| 03.remove | `POST /idaas/clients/remove` | [clients-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/clients-remove.ms) |

## 24.receiver

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.v2 | `POST /idaas/receiver/v2` | [receiver-v2](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/receiver-v2.ms) |
| 02.admin | `POST /idaas/receiver/admin` | [receiver-admin](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/receiver-admin.ms) |

## 25.auth-accounts

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/auth-accounts/list` | [auth-accounts-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-accounts-list.ms) |
| 02.save | `POST /idaas/auth-accounts/save` | [auth-accounts-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-accounts-save.ms) |
| 03.remove | `POST /idaas/auth-accounts/remove` | [auth-accounts-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-accounts-remove.ms) |
| 04.reset | `POST /idaas/auth-accounts/reset` | [auth-accounts-reset](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-accounts-reset.ms) |
| 05.bind-entity | `POST /idaas/auth-accounts/bind-entity` | [auth-accounts-bind-entity](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-accounts-bind-entity.ms) |

## 26.protocol-config

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.read | `GET /idaas/protocol-config/read` | [protocol-config-read](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/protocol-config-read.ms) |
| 02.configure | `POST /idaas/protocol-config/configure` | [protocol-config-configure](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/protocol-config-configure.ms) |
| 03.rotate | `POST /idaas/protocol-config/rotate` | [protocol-config-rotate](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/protocol-config-rotate.ms) |
| 04.disable | `POST /idaas/protocol-config/disable` | [protocol-config-disable](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/protocol-config-disable.ms) |
| 05.federation | `POST /idaas/protocol-config/federation` | [protocol-config-federation](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/protocol-config-federation.ms) |

## 27.auth-account

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.register | `POST /idaas/auth-account/register` | [auth-account-register](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-register.ms) |
| 02.login | `POST /idaas/auth-account/login` | [auth-account-login](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-login.ms) |
| 03.logout | `POST /idaas/auth-account/logout` | [auth-account-logout](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-logout.ms) |
| 04.me | `GET /idaas/auth-account/me` | [auth-account-me](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-me.ms) |
| 05.password | `POST /idaas/auth-account/password` | [auth-account-password](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-password.ms) |
| 06.sessions | `GET /idaas/auth-account/sessions` | [auth-account-sessions](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-sessions.ms) |
| 07.revoke-session | `POST /idaas/auth-account/revoke-session` | [auth-account-revoke-session](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-revoke-session.ms) |
| 08.选择法人办理身份 | `POST /idaas/auth-account/select-entity` | [auth-account-select-entity](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/auth-account-select-entity.ms) |

## 28.mfa

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.status | `POST /idaas/mfa/status` | [mfa-status](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/mfa-status.ms) |
| 02.setup | `POST /idaas/mfa/setup` | [mfa-setup](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/mfa-setup.ms) |
| 03.bind | `POST /idaas/mfa/bind` | [mfa-bind](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/mfa-bind.ms) |
| 04.disable | `POST /idaas/mfa/disable` | [mfa-disable](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/mfa-disable.ms) |
| 05.verify-login | `POST /idaas/mfa/verify-login` | [mfa-verify-login](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/mfa-verify-login.ms) |

## 29.public-entities

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.list | `GET /idaas/public-entities/list` | [public-entities-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/public-entities-list.ms) |
| 02.save | `POST /idaas/public-entities/save` | [public-entities-save](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/public-entities-save.ms) |
| 03.remove | `POST /idaas/public-entities/remove` | [public-entities-remove](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/public-entities-remove.ms) |
| 04.activate-member | `POST /idaas/public-entities/activate-member` | [public-entities-activate-member](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/public-entities-activate-member.ms) |
| 05.revoke-member | `POST /idaas/public-entities/revoke-member` | [public-entities-revoke-member](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/public-entities-revoke-member.ms) |

## 30.verifications

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.provider | `GET /idaas/verifications/provider` | [verifications-provider](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/verifications-provider.ms) |
| 02.save-provider | `POST /idaas/verifications/save-provider` | [verifications-save-provider](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/verifications-save-provider.ms) |
| 03.list | `GET /idaas/verifications/list` | [verifications-list](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/verifications-list.ms) |
| 04.mine | `GET /idaas/verifications/mine` | [verifications-mine](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/verifications-mine.ms) |
| 05.submit | `POST /idaas/verifications/submit` | [verifications-submit](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/verifications-submit.ms) |

## 31.self-entities

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.register | `POST /idaas/self-entities/register` | [self-entities-register](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/self-entities-register.ms) |
| 02.mine | `GET /idaas/self-entities/mine` | [self-entities-mine](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/self-entities-mine.ms) |

## 32.API认证执行

| 名称 | 方法及路径 | 业务源码 |
| --- | --- | --- |
| 01.当前调用身份 | `GET /idaas/api/whoami` | [api-whoami](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/apis/api-whoami.ms) |

## 共享函数

| 名称 | Magic导入路径 | 源码 |
| --- | --- | --- |
| 52.统一认证会话策略 | `@/idaas/auth-session-policy` | [auth-session-policy](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/auth-session-policy.ms) |
| 01.平台权限上下文 | `@/idaas/context` | [context](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/context.ms) |
| 02.功能和对象范围 | `@/idaas/guard` | [guard](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/guard.ms) |
| 03.脱敏审计 | `@/idaas/audit` | [audit](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/audit.ms) |
| 04.事务幂等写入 | `@/idaas/mutate` | [mutate](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/mutate.ms) |
| 05.平台账号登录 | `@/idaas/login` | [login](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/login.ms) |
| 06.平台会话 | `@/idaas/session` | [session](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/session.ms) |
| 07.行业应用管理 | `@/idaas/applications` | [applications](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/applications.ms) |
| 08.应用分组 | `@/idaas/groups` | [groups](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/groups.ms) |
| 09.平台操作账号 | `@/idaas/operators` | [operators](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/operators.ms) |
| 10.平台角色和管理范围 | `@/idaas/platform-roles` | [platform-roles](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/platform-roles.ms) |
| 11.平台配置 | `@/idaas/settings` | [settings](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/settings.ms) |
| 12.个人资料和改密 | `@/idaas/profile` | [profile](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/profile.ms) |
| 13.密码策略 | `@/idaas/password-policy` | [password-policy](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/password-policy.ms) |
| 14.工作区读取 | `@/idaas/bootstrap` | [bootstrap](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/bootstrap.ms) |
| 15.接口审计封装 | `@/idaas/api-call` | [api-call](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/api-call.ms) |
| 16.中央资料管理范围 | `@/idaas/directory-scope` | [directory-scope](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/directory-scope.ms) |
| 17.中央人员机构任职 | `@/idaas/directory` | [directory](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/directory.ms) |
| 18.字典与扩展定义 | `@/idaas/directory-config` | [directory-config](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/directory-config.ms) |
| 19.应用资料分配 | `@/idaas/assignments` | [assignments](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/assignments.ms) |
| 20.扩展值校验及保护 | `@/idaas/field-values` | [field-values](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/field-values.ms) |
| 21.time-window | `@/idaas/time-window` | [time-window](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/time-window.ms) |
| 22.business-catalog | `@/idaas/business-catalog` | [business-catalog](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/business-catalog.ms) |
| 23.effective-access | `@/idaas/effective-access` | [effective-access](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/effective-access.ms) |
| 24.rebuild-projections | `@/idaas/rebuild-projections` | [rebuild-projections](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/rebuild-projections.ms) |
| 25.business-grants | `@/idaas/business-grants` | [business-grants](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/business-grants.ms) |
| 26.permission-groups | `@/idaas/permission-groups` | [permission-groups](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/permission-groups.ms) |
| 27.auth-clients | `@/idaas/auth-clients` | [auth-clients](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/auth-clients.ms) |
| 28.app-list-context | `@/idaas/app-list-context` | [app-list-context](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/app-list-context.ms) |
| 29.sync-config | `@/idaas/sync-config` | [sync-config](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/sync-config.ms) |
| 30.sync-plan | `@/idaas/sync-plan` | [sync-plan](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/sync-plan.ms) |
| 31.sync-execute | `@/idaas/sync-execute` | [sync-execute](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/sync-execute.ms) |
| 32.sync-tasks | `@/idaas/sync-tasks` | [sync-tasks](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/sync-tasks.ms) |
| 33.receiver | `@/idaas/receiver` | [receiver](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/receiver.ms) |
| 34.receiver-apply | `@/idaas/receiver-apply` | [receiver-apply](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/receiver-apply.ms) |
| 35.receiver-admin | `@/idaas/receiver-admin` | [receiver-admin](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/receiver-admin.ms) |
| 36.receiver-account-check | `@/idaas/receiver-account-check` | [receiver-account-check](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/receiver-account-check.ms) |
| 39.protocol-client | `@/idaas/protocol-client` | [protocol-client](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/protocol-client.ms) |
| 40.protocol-access | `@/idaas/protocol-access` | [protocol-access](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/protocol-access.ms) |
| 41.protocol-store | `@/idaas/protocol-store` | [protocol-store](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/protocol-store.ms) |
| 42.protocol-config | `@/idaas/protocol-config` | [protocol-config](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/protocol-config.ms) |
| 43.auth-account-context | `@/idaas/auth-account-context` | [auth-account-context](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/auth-account-context.ms) |
| 44.auth-accounts | `@/idaas/auth-accounts` | [auth-accounts](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/auth-accounts.ms) |
| 45.auth-account-service | `@/idaas/auth-account-service` | [auth-account-service](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/auth-account-service.ms) |
| 46.mfa-service | `@/idaas/mfa-service` | [mfa-service](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/mfa-service.ms) |
| 47.public-entities | `@/idaas/public-entities` | [public-entities](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/public-entities.ms) |
| 48.verification-service | `@/idaas/verification-service` | [verification-service](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/verification-service.ms) |
| 49.platform-domain | `@/idaas/platform-domain` | [platform-domain](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/platform-domain.ms) |
| 50.resource-enabled | `@/idaas/resource-enabled` | [resource-enabled](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/resource-enabled.ms) |
| 51.self-entities | `@/idaas/self-entities` | [self-entities](../../../data-elements/db/migrations/resources/idaas-foundation-20260928/functions/self-entities.ms) |

应用本地用户和角色函数位于 `@/tenant-account/*`。本次退役的8个既有门户/系统管理接口保留原ID和路径，源码见 `data-elements/db/migrations/resources/idaas-local-retirement-20260929/apis`。
