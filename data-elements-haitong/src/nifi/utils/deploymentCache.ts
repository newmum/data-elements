import type { QueryClient } from '@tanstack/react-query';
import type { DeploymentResult, FlowStatusPayload } from '../api/pipelines.ts';

/** Keep a completed deployment ahead of reads that started while it was being compiled. */
export async function acceptDeploymentResult(
  client: QueryClient, result: DeploymentResult, id: string, revision: number,
  sessionIsCurrent: () => boolean,
) {
  const pipelineKey = ['pipeline', id, revision];
  const statusKey = ['pipeline-status', id, revision];
  await Promise.all([
    client.cancelQueries({ queryKey: pipelineKey }),
    client.cancelQueries({ queryKey: statusKey }),
  ]);
  if (!sessionIsCurrent()) return;
  const pipeline = result.pipeline;
  if (pipeline?.id === id) {
    client.setQueryData(pipelineKey, pipeline);
    if (pipeline.nifiProcessGroupId && pipeline.lastDeployedHash && pipeline.status) {
      client.setQueryData<FlowStatusPayload>(statusKey, {
        id, name: pipeline.name, status: pipeline.status, deployed: true,
        processGroupId: pipeline.nifiProcessGroupId,
        lastDeployedHash: pipeline.lastDeployedHash, currentHash: pipeline.lastDeployedHash,
        lastDeployedAt: pipeline.lastDeployedAt, errors: [],
      });
    }
  } else {
    await client.invalidateQueries({ queryKey: pipelineKey });
  }
  if (!sessionIsCurrent()) return;
  void client.invalidateQueries({ queryKey: ['pipelines', revision] });
  void client.invalidateQueries({ queryKey: statusKey });
}
