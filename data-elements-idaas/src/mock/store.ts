import { useSyncExternalStore } from 'react';
import { getDatabase, getSession, getWorkspaceState, subscribeDatabase, subscribeSession, subscribeWorkspace } from '../services/workspace';
export * from '../services/workspace';
/** Compatibility import path; production React now reads only the identity service. */
export function useDatabase() { return useSyncExternalStore(subscribeDatabase, getDatabase); }
export function useSession() { return useSyncExternalStore(subscribeSession, getSession); }
export function useWorkspaceState() { return useSyncExternalStore(subscribeWorkspace, getWorkspaceState); }
