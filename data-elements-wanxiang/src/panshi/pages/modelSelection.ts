import type { LogicalModel } from '../domain/types';

/** A stale model URL must never become the value of an active-only selector. */
export function standardizationModel(models: LogicalModel[], requestedId: string | null) {
  return models.find(model => model.id === requestedId && model.state === 'ACTIVE')
    ?? models.find(model => model.state === 'ACTIVE');
}
