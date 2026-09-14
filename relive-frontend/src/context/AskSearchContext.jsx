import { createContext, useContext, useRef, useState, useCallback } from 'react';
import { searchNatural } from '../services/mediaService';

const AskSearchContext = createContext(null);

export function AskSearchProvider({ children }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  // Change 4 — the AbortController for whichever search is currently in
  // flight, so a newer query can cancel an older, still-pending one.
  const activeControllerRef = useRef(null);
  // Guards against an aborted request's promise still settling out of
  // order before the abort is observed — only the response matching the
  // most recently STARTED search is ever applied to state.
  const latestRequestIdRef = useRef(0);

  const runSearch = useCallback(async (rawQuery) => {
    const trimmed = rawQuery.trim();
    if (!trimmed) return;

    if (activeControllerRef.current) {
      activeControllerRef.current.abort();
    }
    const controller = new AbortController();
    activeControllerRef.current = controller;
    const requestId = ++latestRequestIdRef.current;

    setQuery(rawQuery);
    setSearched(true);
    setLoading(true);

    try {
      const data = await searchNatural(trimmed, controller.signal);
      if (requestId !== latestRequestIdRef.current) return; // superseded
      setResults(data);
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') {
        return; // expected — a newer query cancelled this one
      }
      if (requestId !== latestRequestIdRef.current) return; // superseded
      setResults([]);
    } finally {
      if (requestId === latestRequestIdRef.current) setLoading(false);
    }
  }, []);

  const value = { query, setQuery, results, loading, searched, runSearch };
  return (
    <AskSearchContext.Provider value={value}>
      {children}
    </AskSearchContext.Provider>
  );
}

export function useAskSearch() {
  const ctx = useContext(AskSearchContext);
  if (!ctx) throw new Error('useAskSearch must be used within an AskSearchProvider');
  return ctx;
}