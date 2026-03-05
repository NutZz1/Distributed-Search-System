import { useEffect, useState, useCallback } from 'react';
import './index.css';
import { getHealth, searchDocuments } from './api';
import SearchBar from './components/SearchBar';
import ResultCard from './components/ResultCard';
import IndexForm from './components/IndexForm';
import ClusterMap from './components/ClusterMap';
import NodeControl from './components/NodeControl';

const TABS = [
  { id: 'search', label: '🔍 Search' },
  { id: 'index', label: '📄 Index' },
  { id: 'cluster', label: '🗺 Cluster' },
  { id: 'nodes', label: '⚡ Nodes' },
];

export default function App() {
  const [tab, setTab] = useState('search');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);
  const [searching, setSearching] = useState(false);
  const [online, setOnline] = useState(null);      // null = checking
  const [toasts, setToasts] = useState([]);

  // ---- Health polling ----
  useEffect(() => {
    const check = async () => setOnline(await getHealth());
    check();
    const id = setInterval(check, 5000);
    return () => clearInterval(id);
  }, []);

  // ---- Toast helper ----
  const addToast = useCallback((message, type = 'info') => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4000);
  }, []);

  // ---- Search handler ----
  const handleSearch = async () => {
    if (!query.trim()) return;
    setSearching(true);
    setResults(null);
    try {
      const docs = await searchDocuments(query.trim());
      setResults(docs);
      if (docs.length === 0) addToast('No results found.', 'info');
    } catch (err) {
      addToast('Search failed: ' + err.message, 'error');
      setResults([]);
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="app">
      {/* ---- Header ---- */}
      <header className="header">
        <div className="header-title">
          <span>🔎</span>
          <h1>Distributed Search Dashboard</h1>
        </div>
        <div
          className={`status-badge ${online === null ? 'loading' : online ? 'online' : 'offline'
            }`}
        >
          <span className="dot" />
          {online === null ? 'Checking…' : online ? 'Cluster Online' : 'Cluster Offline'}
        </div>
      </header>

      {/* ---- Tab bar ---- */}
      <div className="tabs" role="tablist">
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            role="tab"
            aria-selected={tab === id}
            className={`tab-btn ${tab === id ? 'active' : ''}`}
            onClick={() => setTab(id)}
            id={`tab-${id}`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* ---- Tab content ---- */}
      {tab === 'search' && (
        <section>
          <SearchBar
            value={query}
            onChange={setQuery}
            onSearch={handleSearch}
            loading={searching}
          />
          {results === null && !searching && (
            <div className="empty-state">
              <div className="icon">🔍</div>
              <p>Enter a query above to search across all shards.</p>
            </div>
          )}
          {searching && (
            <div className="empty-state">
              <span className="spinner" style={{ width: 28, height: 28 }} />
              <p style={{ marginTop: 12 }}>Querying shard masters…</p>
            </div>
          )}
          {results !== null && !searching && (
            <>
              <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 12 }}>
                {results.length} result{results.length !== 1 ? 's' : ''} for &ldquo;{query}&rdquo;
              </p>
              {results.length > 0 ? (
                <div className="result-list">
                  {results.map((doc) => (
                    <ResultCard key={doc.id} doc={doc} />
                  ))}
                </div>
              ) : (
                <div className="empty-state">
                  <div className="icon">📭</div>
                  <p>No documents matched your query.</p>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {tab === 'index' && (
        <section className="card">
          <p className="section-title">Index a Document</p>
          <p className="section-subtitle">
            Documents are routed to the correct shard MASTER automatically.
          </p>
          <IndexForm onToast={addToast} />
        </section>
      )}

      {tab === 'cluster' && (
        <section className="card">
          <ClusterMap />
        </section>
      )}

      {tab === 'nodes' && (
        <section>
          <NodeControl onToast={addToast} />
        </section>
      )}

      {/* ---- Toast container ---- */}
      <div className="toast-container" aria-live="polite">
        {toasts.map(({ id, message, type }) => (
          <div key={id} className={`toast ${type}`}>{message}</div>
        ))}
      </div>
    </div>
  );
}
