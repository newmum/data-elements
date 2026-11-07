export default function ConnectionTreeLabel({ label, count, badge, connected }: {
  label: string;
  count?: number;
  badge?: string;
  connected?: boolean;
}) {
  return <span className="connection-tree-label">
    <span className="connection-tree-text" title={label}>{label}</span>
    {count !== undefined && <span className="connection-tree-count">{count}</span>}
    {badge && <span className="connection-tree-badge">{badge}</span>}
    {connected !== undefined && <i className={`connection-tree-status ${connected ? 'online' : 'offline'}`}/>}
  </span>;
}
