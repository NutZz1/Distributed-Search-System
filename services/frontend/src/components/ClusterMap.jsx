import { useEffect, useRef, useState } from 'react';
import { getClusterTopology } from '../api';

const ALL_NODES = ['search-node-1', 'search-node-2', 'search-node-3', 'search-node-4'];
const POLL_MS = 2000;

function roleBadge(role) {
    if (!role) return <span style={{ color: 'var(--border)', fontWeight: 600 }}>—</span>;
    if (role === 'MASTER') return <span className="badge badge-master">MASTER</span>;
    if (role === 'SLAVE') return <span className="badge badge-slave">SLAVE</span>;
    if (role === 'OFFLINE') return <span className="badge badge-offline">OFFLINE</span>;
    return <span className="badge badge-info">{role}</span>;
}

export default function ClusterMap() {
    const [topology, setTopology] = useState({});   // partition -> { node -> role }
    const [error, setError] = useState(null);
    const [lastUpdated, setLastUpdated] = useState(null);
    const prevRef = useRef({});

    const fetchTopology = async () => {
        try {
            const data = await getClusterTopology();
            prevRef.current = topology;
            setTopology(data);
            setLastUpdated(new Date().toLocaleTimeString());
            setError(null);
        } catch (e) {
            setError('Could not reach cluster.');
        }
    };

    useEffect(() => {
        fetchTopology();
        const id = setInterval(fetchTopology, POLL_MS);
        return () => clearInterval(id);
    }, []);

    const partitions = Object.keys(topology).sort();

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                <p className="section-title" style={{ margin: 0 }}>Live Cluster Topology</p>
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {error
                        ? <span style={{ color: 'var(--danger)' }}>⚠ {error}</span>
                        : lastUpdated ? `Updated ${lastUpdated}` : 'Loading…'}
                    &nbsp;<span style={{ fontSize: 11 }}>· polls every 2s</span>
                </span>
            </div>

            {partitions.length === 0 ? (
                <div className="empty-state">
                    <div className="icon">🔄</div>
                    <p>Waiting for Helix to report cluster state…</p>
                    <p style={{ marginTop: 6, fontSize: 12, color: 'var(--text-muted)' }}>
                        Make sure all containers are running.
                    </p>
                </div>
            ) : (
                <div className="cluster-map">
                    {/* Header row */}
                    <div className="cell header-cell">Shard</div>
                    {ALL_NODES.map((n) => (
                        <div className="cell header-cell" key={n}>{n}</div>
                    ))}

                    {/* Data rows — one per partition */}
                    {partitions.map((partition) => {
                        const stateMap = topology[partition] || {};
                        return (
                            <div key={partition} style={{ display: 'contents' }}>
                                <div className="cell row-label">
                                    <span className="badge badge-shard" style={{ fontSize: 11 }}>
                                        {partition.replace('search-index_', 'Shard ')}
                                    </span>
                                </div>
                                {ALL_NODES.map((node) => {
                                    const role = stateMap[node];
                                    const prevRole = (prevRef.current[partition] || {})[node];
                                    const changed = prevRole && prevRole !== role;
                                    return (
                                        <div
                                            className={`cell role-cell ${changed ? 'changed' : ''}`}
                                            key={node}
                                        >
                                            {roleBadge(role)}
                                        </div>
                                    );
                                })}
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Legend */}
            <div style={{ display: 'flex', gap: 12, marginTop: 16, flexWrap: 'wrap' }}>
                <span className="badge badge-master">MASTER</span>
                <span style={{ fontSize: 12, color: 'var(--text-muted)', alignSelf: 'center' }}>Accepts writes, serves reads</span>
                <span className="badge badge-slave">SLAVE</span>
                <span style={{ fontSize: 12, color: 'var(--text-muted)', alignSelf: 'center' }}>Receives replicated docs, serves reads</span>
                <span style={{ color: 'var(--border)', fontWeight: 600, alignSelf: 'center' }}>—</span>
                <span style={{ fontSize: 12, color: 'var(--text-muted)', alignSelf: 'center' }}>Unassigned to this shard</span>
            </div>
        </div>
    );
}
