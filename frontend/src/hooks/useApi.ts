import { useCallback, useEffect, useState, type DependencyList } from 'react';
import { errorMessage } from '../api/errors';

/**
 * Loads data when the component mounts and whenever `deps` change.
 * Answers that arrive after a newer request started are ignored (no flicker, no stale data).
 */
export function useApi<T>(load: () => Promise<T>, deps: DependencyList) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(null);
    load()
      .then((result) => current && setData(result))
      .catch((e) => current && setError(errorMessage(e)))
      .finally(() => current && setLoading(false));
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { data, setData, loading, error, reload };
}
