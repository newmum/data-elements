package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 数据接入任务表
 * </p>
 *
 * @author author
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("data_access_agg_task_t")
@ApiModel(value="DataAccessAggTaskT对象", description="数据接入任务表")
public class DataAccessAggTaskT implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键（任务id）")
    @TableId(value = "tid", type = IdType.ASSIGN_ID)
    private String tid;

    @ApiModelProperty(value = "资源目录id")
    private String dataCatalogId;

    @ApiModelProperty(value = "任务名称")
    private String taskName;

    @ApiModelProperty(value = "任务类型名称")
    private String taskTypeName;

    @ApiModelProperty(value = "任务类型id（任务组上级id）")
    private String taskTypeId;

    @ApiModelProperty(value = "任务描述")
    private String taskDesc;

    @ApiModelProperty(value = "源数据库id")
    private String sourceDbId;

    @ApiModelProperty(value = "源数据库表id")
    private String sourceTableId;

    @ApiModelProperty(value = "源数据库表主键")
    private String sourceTablePrimaryKey;

    @ApiModelProperty(value = "源数据库表增量字段")
    private String sourceTableIncrementKey;

    @ApiModelProperty(value = "目标数据库id")
    private String targetDbId;

    @ApiModelProperty(value = "目标数据库表id")
    private String targetTableId;

    @ApiModelProperty(value = "目标数据库表组件")
    private String targetTablePrimaryKey;

    @ApiModelProperty(value = "调度策略（0 实时 1 周期 2 cron）")
    private Integer scheduleStrategy;

    @ApiModelProperty(value = "调度周期（0 每日 1 每周 2 每月  ）")
    private Integer scheduleCycle;

    @ApiModelProperty(value = "执行时间 （yyyy-MM-dd HH:mm:ss）")
    private String scheduleRunning;

    @ApiModelProperty(value = "生效时间开始（yyyy-MM-dd HH:mm:ss）")
    private String scheduleRunningStart;

    @ApiModelProperty(value = "生效时间结束（yyyy-MM-dd HH:mm:ss）")
    private String scheduleRunningEnd;

    @ApiModelProperty(value = "失败重试次数（0 不重试 1一次 3 三次 ）")
    private Integer scheduleFaildRetry;

    @ApiModelProperty(value = "cron表达式")
    private String cronExpress;

    @ApiModelProperty(value = "任务状态（1 运行中 0 未启用 2 运行异常）")
    private Integer taskStatus;

    @ApiModelProperty(value = "最新运行时间")
    private String lastRunning;

    @ApiModelProperty(value = "最新状态值")
    private String lastStatusValue;

    @ApiModelProperty(value = "结束时间")
    private String endRunning;

    @ApiModelProperty(value = "租户号")
    private String tenantId;

    @ApiModelProperty(value = "乐观锁")
    private String revision;

    @ApiModelProperty(value = "创建人")
    private String createdBy;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdTime;

    @ApiModelProperty(value = "更新人")
    private String updatedBy;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedTime;

    @ApiModelProperty(value = "任务组id")
    private String processGroupId;

    @ApiModelProperty(value = "任务组版本号")
    private Integer processGroupVersion;

    @ApiModelProperty(value = "是否删除")
    private Integer isDel;

    @ApiModelProperty(value = "nifi pipline 本地配置文件id")
    private String pipelineId;

    @ApiModelProperty(value = "最新监控时间")
    private LocalDateTime monitorTime;

    @ApiModelProperty(value = "延时等级")
    private String delayLevel;

    @ApiModelProperty(value = "估算延时毫秒")
    private Long delayMsEstimate;

    @ApiModelProperty(value = "超时阈值毫秒")
    private Long thresholdMs;

    @ApiModelProperty(value = "是否超时")
    private Integer isTimeout;

    @ApiModelProperty(value = "排队数量")
    private Long queuedCount;

    @ApiModelProperty(value = "排队字节数")
    private Long queuedBytes;

    @ApiModelProperty(value = "活动线程数")
    private Integer activeThreadCount;

    @ApiModelProperty(value = "流入文件数")
    private Long flowFilesIn;

    @ApiModelProperty(value = "流出文件数")
    private Long flowFilesOut;

    @ApiModelProperty(value = "流入字节数")
    private Long bytesIn;

    @ApiModelProperty(value = "流出字节数")
    private Long bytesOut;

    @ApiModelProperty(value = "监控状态")
    private String monitorStatus;

    @ApiModelProperty(value = "监控消息")
    private String monitorMsg;
}
