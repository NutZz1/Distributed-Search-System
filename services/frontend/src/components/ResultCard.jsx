export default function ResultCard({ doc }) {
    const shardId = Math.abs(doc.id % 2);

    return (
        <div className="result-card">
            <div className="result-card-header">
                <span className="badge badge-id">#{doc.id}</span>
                <span className="badge badge-shard">Shard {shardId}</span>
                <h3>{doc.title}</h3>
            </div>
            <p>{doc.content}</p>
            {doc.url && (
                <a href={doc.url} target="_blank" rel="noreferrer">
                    {doc.url}
                </a>
            )}
        </div>
    );
}
