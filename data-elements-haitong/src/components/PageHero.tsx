import type { ArtKind } from '../design/OceanArt';
import { useOceanTheme } from '../design/theme';

const ART_REVISION = 'hero-refine-20261005';

const heroCopy: Record<ArtKind, { alt: string }> = {
  overview: { alt: '来源数据经处理后落地目标，同时展示运行监测' },
  tasks: { alt: '采集、转换、写入与校验的任务进度' },
  nifi: { alt: 'NiFi 数据采集、转换、写入与校验的流程节点' },
  registry: { alt: 'NiFi 流程登记与租户内唯一校验' },
  batch: { alt: '四张来源表汇入待创建任务队列' },
  multi: { alt: '三张表分别关联独立运行的流程' },
  ingress: { alt: '源端记录沿处理链路进入目标库' },
  distribution: { alt: '来源任务向三个目标系统分发' },
  clusters: { alt: 'NiFi 集群节点拓扑与心跳状态' },
  cross: { alt: '发送与接收网络分区之间的传输服务待接入' },
  instant: { alt: '来源和目标记录进行即时差异核对' },
  inventory: { alt: '来源和目标清单执行主键与内容校验' },
  statements: { alt: '盘点历史记录、差异状态与脱敏导出' },
};

export default function PageHero({
  title,
  description,
  kind = 'overview',
  primaryAction,
  tags = [],
}: {
  kicker: string;
  title: string;
  description: string;
  kind?: ArtKind;
  primaryAction?: React.ReactNode;
  tags?: string[];
}) {
  const { dark } = useOceanTheme();
  const asset = `${import.meta.env.BASE_URL}heroes/${kind}-${dark ? 'dark' : 'light'}.svg?v=${ART_REVISION}`;
  const alt = heroCopy[kind]?.alt || '海通业务插画';

  return (
    <section className={`ht-hero hero-${kind}`}>
      <div className="ht-hero-copy">
        <h1>{title}</h1>
        <p>{description}</p>
        {tags.length > 0 && (
          <div className="ht-hero-tags">
            {tags.map((t) => (
              <span key={t}>{t}</span>
            ))}
          </div>
        )}
        {primaryAction && <div className="ht-hero-action">{primaryAction}</div>}
      </div>
      <div className="ht-hero-art">
        <img className="ht-hero-art-image" src={asset} alt={alt} loading="eager" decoding="async" />
      </div>
    </section>
  );
}
