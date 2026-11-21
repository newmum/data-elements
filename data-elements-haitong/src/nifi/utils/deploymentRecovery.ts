import type { DeploymentResult, Pipeline } from '../api/pipelines';

export function uncertainDeployment(error: any): boolean {
  return ['ECONNABORTED', 'ETIMEDOUT', 'ERR_NETWORK'].includes(error?.code)
    || [502, 504].includes(error?.response?.status);
}

/** A lost HTTP response must be reconciled through reads, never another deploy request. */
export async function recoverDeployment(before: Pipeline, read: () => Promise<Pipeline>,
  current: () => boolean, pause: () => Promise<void>, attempts = 60): Promise<DeploymentResult | undefined> {
  if (!before.designHash) return;
  for (let attempt = 0; attempt < attempts && current(); attempt++) {
    try {
      const after = await read();
      if (!current() || after.id !== before.id || after.designHash !== before.designHash) return;
      const candidate = after.nodeMapping?.candidate;
      if (candidate && candidate.designHash === after.designHash
        && candidate.processGroupId !== before.nodeMapping?.candidate?.processGroupId) {
        return { pipeline: after, processGroupId: candidate.processGroupId, mode: 'candidate-created', started: false };
      }
      if (after.nifiProcessGroupId && after.lastDeployedHash && after.lastDeployedAt
        && after.lastDeployedAt !== before.lastDeployedAt && ['STOPPED', 'RUNNING'].includes(after.status ?? '')) {
        return { pipeline: after, processGroupId: after.nifiProcessGroupId, mode: 'deployment-confirmed-by-readback',
          started: after.status === 'RUNNING' };
      }
      if (after.status === 'DEPLOY_FAILED' && after.updatedAt !== before.updatedAt) return;
    } catch { /* A transient read failure cannot justify replaying a deployment. */ }
    if (attempt + 1 < attempts && current()) await pause();
  }
}
