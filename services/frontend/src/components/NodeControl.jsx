import { useState, useEffect } from 'react';
import { stopNode, startNode, getNodeStatus } from '../api';

const NODES = [
    { id: 'search-node-1', port: 9001, shard: 'Shard 0 (primary)' },
    { id: 'search-node-2', port: 9002, shard: 'Shard 0 (replica)' },
    { id: 'search-node-3', port: 9003, shard: 'Shard 1 (primary)' },
    { id: 'search-node-4', port: 9004, shard: 'Shard 1 (replica)' },
];

export default function NodeControl({ onToast }) {
    // Track which nodes are currently processing a click
    const [loading, setLoading] = useState({});

    // Track actual status fetched from backend ('running', 'exited', etc)
    const [statuses, setStatuses] = useState({});

    // Poll actual Docker state every 2 seconds
    useEffect(() => {
        const fetchAll = async () => {
            for (const node of NODES) {
                try {
                    const { status } = await getNodeStatus(node.id);
                    setStatuses(prev => ({ ...prev, [node.id]: status }));
                } catch (e) { /* ignore polling errors */ }
            }
        };
        fetchAll();
        const id = setInterval(fetchAll, 2000);
        return () => clearInterval(id);
    }, []);

    const setNodeLoading = (id, val) =>
        setLoading((prev) => ({ ...prev, [id]: val }));

    const handleStop = async (nodeId) => {
        setNodeLoading(nodeId, true);
        try {
            await stopNode(nodeId);
            setStatuses((prev) => ({ ...prev, [nodeId]: 'exited' }));
            onToast(`${nodeId} stopped. Helix will re-elect MASTER…`, 'info');
        } catch (err) {
            onToast(`Failed to stop ${nodeId}: ${err.message}`, 'error');
        } finally {
            setNodeLoading(nodeId, false);
        }
    };

    const handleStart = async (nodeId) => {
        setNodeLoading(nodeId, true);
        try {
            await startNode(nodeId);
            setStatuses((prev) => ({ ...prev, [nodeId]: 'running' }));
            onToast(`${nodeId} started. Node will rejoin cluster as SLAVE.`, 'success');
        } catch (err) {
            onToast(`Failed to start ${nodeId}: ${err.message}`, 'error');
        } finally {
            setNodeLoading(nodeId, false);
        }
    };

    return (
        <div>
            <p className="section-title">Node Control</p>
            <p className="section-subtitle">
                Stop a node to simulate a failure. Watch the Cluster tab — Helix will automatically
                elect a new MASTER within ~5 seconds.
            </p>
            <div className="node-grid">
                {NODES.map(({ id, port, shard }) => {
                    const status = statuses[id];
                    // Consider it stopped if it's explicitly exited/dead or still fetching
                    const isStopped = status !== 'running' && status !== undefined;
                    const isLoading = loading[id] || status === undefined;

                    return (
                        <div key={id} className={`node-card ${isStopped ? 'node-stopped' : ''}`}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                                <h3>{id}</h3>
                                {isStopped
                                    ? <span className="badge badge-offline">{status ? status.toUpperCase() : 'STOPPED'}</span>
                                    : <span className="badge badge-master" style={{ background: '#d1fae5', color: '#065f46' }}>RUNNING</span>
                                }
                            </div>
                            <p className="node-meta">
                                Port {port} · {shard}
                            </p>
                            <div className="node-actions">
                                {isStopped ? (
                                    <button
                                        className="btn btn-success btn-sm"
                                        onClick={() => handleStart(id)}
                                        disabled={isLoading}
                                        id={`start-${id}`}
                                    >
                                        {isLoading ? <span className="spinner" /> : '▶'} Start
                                    </button>
                                ) : (
                                    <button
                                        className="btn btn-danger btn-sm"
                                        onClick={() => handleStop(id)}
                                        disabled={isLoading}
                                        id={`stop-${id}`}
                                    >
                                        {isLoading ? <span className="spinner" /> : '■'} Stop
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            <div className="card" style={{ marginTop: 20, background: '#fffbeb', borderColor: '#fde68a' }}>
                <p style={{ fontSize: 13, color: '#92400e' }}>
                    ⚡ <strong>How it works:</strong> Clicking Stop calls the Docker Engine API via the query-router
                    to actually stop the container — identical to stopping it in Docker Desktop.
                    Helix detects the ZooKeeper session drop within seconds and re-elects a new MASTER.
                    Click Start to bring the node back; it rejoins as SLAVE and syncs from the MASTER.
                </p>
            </div>
        </div>
    );
}
