import { useState } from 'react';
import { indexDocument } from '../api';

const NODES = ['search-node-1', 'search-node-2', 'search-node-3', 'search-node-4'];

export default function IndexForm({ onToast }) {
    const [form, setForm] = useState({ id: '', title: '', content: '', url: '' });
    const [loading, setLoading] = useState(false);

    const shardId = form.id !== '' ? Math.abs(parseInt(form.id || '0') % 2) : null;

    const handleChange = (field) => (e) =>
        setForm((f) => ({ ...f, [field]: e.target.value }));

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.id || !form.title) {
            onToast('ID and Title are required.', 'error');
            return;
        }
        setLoading(true);
        try {
            await indexDocument({
                id: parseInt(form.id),
                title: form.title,
                content: form.content,
                url: form.url,
            });
            onToast(`Document #${form.id} indexed to Shard ${shardId}!`, 'success');
            setForm({ id: '', title: '', content: '', url: '' });
        } catch (err) {
            onToast(err.message, 'error');
        } finally {
            setLoading(false);
        }
    };

    return (
        <form onSubmit={handleSubmit}>
            {shardId !== null && (
                <div className="shard-hint">
                    📦 Document will go to <strong>Shard {shardId}</strong>
                    &nbsp;·&nbsp; MASTER for this shard is determined by Helix
                </div>
            )}

            <div className="form-grid">
                <div className="form-group">
                    <label>Document ID *</label>
                    <input
                        type="number"
                        placeholder="e.g. 1"
                        value={form.id}
                        onChange={handleChange('id')}
                        min="0"
                        id="doc-id"
                    />
                </div>
                <div className="form-group">
                    <label>URL</label>
                    <input
                        type="url"
                        placeholder="https://example.com"
                        value={form.url}
                        onChange={handleChange('url')}
                        id="doc-url"
                    />
                </div>
                <div className="form-group full">
                    <label>Title *</label>
                    <input
                        type="text"
                        placeholder="Document title"
                        value={form.title}
                        onChange={handleChange('title')}
                        id="doc-title"
                    />
                </div>
                <div className="form-group full">
                    <label>Content</label>
                    <textarea
                        placeholder="Document content…"
                        value={form.content}
                        onChange={handleChange('content')}
                        id="doc-content"
                    />
                </div>
            </div>

            <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
                <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={loading}
                    id="index-submit-btn"
                >
                    {loading ? <span className="spinner" /> : '📄'} Index Document
                </button>
            </div>
        </form>
    );
}
