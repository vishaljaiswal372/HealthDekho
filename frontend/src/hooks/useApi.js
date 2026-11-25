import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs an async loader on mount (and when deps change) and tracks loading / error / data.
 * Results from an outdated call (e.g. after deps changed again) are ignored.
 */
export function useApi(loader, deps = [], { enabled = true } = {}) {
  const [state, setState] = useState({ data: null, loading: enabled, error: null });
  const callId = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(loader, deps);

  const reload = useCallback(async () => {
    const id = ++callId.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await run();
      if (id === callId.current) setState({ data, loading: false, error: null });
      return data;
    } catch (error) {
      if (id === callId.current) setState({ data: null, loading: false, error });
      return undefined;
    }
  }, [run]);

  useEffect(() => {
    if (enabled) reload();
  }, [reload, enabled]);

  return { ...state, reload };
}
