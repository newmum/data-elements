-- 海通流程轻量版 H2 控制库。应用启动时幂等执行。
CREATE TABLE IF NOT EXISTS haitong_flow (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    dsl CLOB NOT NULL,
    design_version BIGINT NOT NULL,
    group_id VARCHAR(36),
    deployed_version BIGINT,
    native_mapping CLOB,
    retired_group_id VARCHAR(36),
    updated_at BIGINT NOT NULL
);
COMMENT ON TABLE haitong_flow IS '流程管理-独立 NiFi 流程设计与部署关联';
COMMENT ON COLUMN haitong_flow.id IS '流程标识';
COMMENT ON COLUMN haitong_flow.name IS '流程名称';
COMMENT ON COLUMN haitong_flow.dsl IS '流程节点、连线及配置 JSON';
COMMENT ON COLUMN haitong_flow.design_version IS '当前画布保存版本';
COMMENT ON COLUMN haitong_flow.group_id IS '当前 NiFi 流程组标识';
COMMENT ON COLUMN haitong_flow.deployed_version IS '当前部署使用的画布版本';
COMMENT ON COLUMN haitong_flow.native_mapping IS '画布节点与 NiFi 处理器及连线映射 JSON';
COMMENT ON COLUMN haitong_flow.retired_group_id IS '待清理的上一个 NiFi 流程组标识';
COMMENT ON COLUMN haitong_flow.updated_at IS '更新时间，Unix 毫秒';
CREATE INDEX IF NOT EXISTS idx_haitong_flow_updated ON haitong_flow(updated_at, id);
