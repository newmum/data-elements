/** Serializes persistence requests: a slow old write must not overwrite a newer revision. */
export function createSerialQueue() {
  let tail: Promise<void> = Promise.resolve();
  return function enqueue<T>(task: () => Promise<T>): Promise<T> {
    const current = tail.then(task, task);
    tail = current.then(() => undefined, () => undefined);
    return current;
  };
}
