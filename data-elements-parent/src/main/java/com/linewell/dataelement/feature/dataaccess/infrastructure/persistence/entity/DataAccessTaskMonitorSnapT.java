package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("data_access_task_monitor_snap_t")
@ApiModel(value = "DataAccessTaskMonitorSnapT对象", description = "数据接入任务监控快照表")
public class DataAccessTaskMonitorSnapT implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    @TableId(value = "tid", type = IdType.ASSIGN_ID)
    private String tid;

    @ApiModelProperty(value = "任务id")
    private String taskId;

    @ApiModelProperty(value = "pipeline配置id")
    private String pipelineId;

    @ApiModelProperty(value = "process group id")
    private String processGroupId;

    @ApiModelProperty(value = "监控时间")
    private LocalDateTime monitorTime;

    @ApiModelProperty(value = "延时等级: NORMAL(无积压), MINOR(轻微积压), SEVERE(严重积压), BLOCKED(阻塞), ERROR(监控异常)")
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

    @ApiModelProperty(value = "监控状态: SUCCESS(采集成功), BLOCKED(阻塞), ERROR(监控异常)")
    private String monitorStatus;

    @ApiModelProperty(value = "监控消息")
    private String monitorMsg;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdTime;

    @ApiModelProperty(value = "是否删除")
    private Integer isDel;
}
