import { useEffect, useState } from "react";
import { Button, Empty, Spin, Alert } from "antd";
import App from "./App";
import {
  MockMetadataClient,
  metadataHome,
  readSharedCatalog,
  readSharedWorkspace,
  type SharedWorkspace,
} from "./sharedWorkspace";

export default function SharedFengchi() {
  const [workspace, setWorkspace] = useState<SharedWorkspace>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let disposed = false;
    let generation = 0;
    const client = new MockMetadataClient((message) => {
      if (disposed) return;
      generation++;
      setWorkspace(undefined);
      setError(message);
      setLoading(false);
    });
    void readSharedCatalog(client)
      .then((base) => {
        const selectSource = (id: string, refresh = false) => {
          const current = ++generation;
          setLoading(true);
          setError("");
          void (async () => {
            if (refresh) base = await readSharedCatalog(client);
            return readSharedWorkspace(client, base, id, selectSource);
          })()
            .then((next) => {
              if (!disposed && current === generation) {
                setWorkspace(next);
                const destination = new URL(location.href);
                destination.searchParams.set("sourceId", next.source.tid);
                history.replaceState(history.state, "", destination);
              }
            })
            .catch((e) => {
              if (!disposed && current === generation) setError(e.message);
            })
            .finally(() => {
              if (!disposed && current === generation) setLoading(false);
            });
        };
        if (base.sources.length)
          selectSource(
            new URLSearchParams(location.search).get("sourceId") ||
              base.sources[0].tid,
          );
        else {
          setError("当前行业没有可访问的数据源");
          setLoading(false);
        }
      })
      .catch((e) => {
        if (!disposed) {
          setError(e.message);
          setLoading(false);
        }
      });
    return () => {
      disposed = true;
      client.dispose();
    };
  }, []);
  if (!workspace)
    return (
      <div style={{ padding: 40 }}>
        {loading ? (
          <Spin tip="正在读取共享目录"><div style={{minHeight:80}} /></Spin>
        ) : (
          <>
            <Empty description={error} />
            <Button href={metadataHome}>返回元数据管理平台</Button>
          </>
        )}
      </div>
    );
  return (
    <Spin
      wrapperClassName="shared-fengchi-workspace"
      style={{ height: "100vh" }}
      spinning={loading}
      tip="正在读取数据源"
    >
      {error && <Alert type="error" message={error} closable />}
      <App key={workspace.source.tid} workspace={workspace} />
    </Spin>
  );
}
