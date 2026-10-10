# 画布数据源图标

本目录保存画布使用的本地图标。`databaseIcons.tsx` 统一供组件选择器、画布节点和配置抽屉使用；JDBC 目标节点按实际 `dbType` 选择图标。构建时打包本地资源，不使用远程图片地址，不按列表行调用接口获取图标。

保留品牌原色，用中性底板确保深浅色模式都能识别；不再用自绘数据库圆柱与字母缩写代替品牌。API、FTP、SFTP 是通用协议，使用工程现有 Ant Design 图标库的接口、文件夹、锁图标，不冒充厂商标识。GBase 两个版本共用 GBase 品牌；TDSQL 两个版本共用腾讯云品牌；HetuEngine 使用华为品牌，具体产品由旁边的文字区分。

## 本次补充资源与来源

以下资源保持上游图形。ICO 文件仅解码为 PNG；神通保留完整官方图片，界面以 `object-position` 显示其左侧标识，不拉伸图像。

| 本地文件 | 来源 | 原始资源 |
| --- | --- | --- |
| `clickhouse.png` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.clickhouse/icons/clickhouse_icon_big@2x.png) |
| `db2.svg` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.db2/icons/db2_icon_big.svg) |
| `doris.svg` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.doris/icons/doris_icon_big.svg) |
| `elasticsearch.png` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.generic/icons/elasticsearch_icon_big@2x.png) |
| `iotdb.png` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.iotdb/icons/iotdb_icon_big@2x.png) |
| `mariadb.png` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.mysql/icons/mariadb_icon_big@2x.png) |
| `sqlserver.png` | DBeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.mssql/icons/mssql_icon_big@2x.png) |
| `starrocks.svg` | dbeaver | [原始资源](https://raw.githubusercontent.com/dbeaver/dbeaver/71cee3a752a7f5b967a39cec50d9b971a2ba43a6/plugins/org.jkiss.dbeaver.ext.starrocks/icons/starrocks_icon_big.svg) |
| `hbase.svg` | official | [原始资源](https://hbase.apache.org/images/logo.svg) |
| `hdfs.png` | official | [原始资源](https://hadoop.apache.org/elephant.png) |
| `oscar.png` | official | [原始资源](https://shentongdata.com/data/attachment/2019-12/2019121608431415700s.png) |
| `hailiang.png` | official | [原始资源](https://docs.vastdata.com.cn/favicon.ico) |
| `tdsql.png` | official | [原始资源](https://cloudcache.tencent-cloud.com/qcloud/favicon.ico) |
| `highgo.png` | official favicon; ICO decoded to PNG | [原始资源](https://highgo.com/favicon.ico) |
| `kafka.svg` | Simple Icons CC0; 添加品牌色 | [原始资源](https://raw.githubusercontent.com/simple-icons/simple-icons/98820a4dc8c363ca72fa2c0d294ea4a0a9bba75d/icons/apachekafka.svg) |
| `minio.svg` | Simple Icons CC0; 添加品牌色 | [原始资源](https://raw.githubusercontent.com/simple-icons/simple-icons/98820a4dc8c363ca72fa2c0d294ea4a0a9bba75d/icons/minio.svg) |
| `hetu.svg` | Simple Icons CC0; 添加品牌色 | [原始资源](https://raw.githubusercontent.com/simple-icons/simple-icons/98820a4dc8c363ca72fa2c0d294ea4a0a9bba75d/icons/huawei.svg) |

## 继承原工程的图标

`dameng.svg`、`gaussdb.svg`、`gbase8a.svg`、`hive.svg`、`kingbase8.svg`、`mysql.svg`、`oceanbasemysql.svg`、`oracle.svg`、`postgresql.svg` 为原工程已有的品牌造型，本次直接复用；原工程未记录其下载地址及作者，不能声称这些文件由本次从官网下载。

## 许可与维护

- DBeaver 资源来自上述固定提交，遵循 Apache License 2.0；完整许可证见 `LICENSE-DBeaver.txt`，上游作者为 DBeaver Corp 及贡献者。
- Simple Icons 资源遵循 [CC0](https://github.com/simple-icons/simple-icons/blob/develop/LICENSE.md)；本地仅添加品牌颜色，图形路径保持不变。
- 官网资源及产品商标归各自权利人所有，本系统仅用于标识实际连接的数据源；保留厂商／项目原色和品牌造型。
- 新增数据源时同时补充本地图标及来源，在浅色、深色和支持的最低浏览器版本检查清晰度。不要在运行时引用在线 CDN。
