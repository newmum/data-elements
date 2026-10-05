package com.linewell.dataelement.platform.scheduling;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("scheduledTask")
public class ScheduledTaskFacade {

    @Comment("定时任务执行")
    public void scheduledRun(@Comment("定时任务执行动作") String action, @Comment("定时任务执行参数") Object param) {
        //1、bean名称需要和action相同，在这里自动分发到对应实现类
        //2、统一调用RunnableTask接口的run方法，定时器实现需要实现RunnableTask接口
        SpringUtil.getBean(action, RunnableTask.class).run(param);
    }
}
