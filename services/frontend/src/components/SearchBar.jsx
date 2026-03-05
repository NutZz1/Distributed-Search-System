export default function SearchBar({ value, onChange, onSearch, loading }) {
    const handleKey = (e) => {
        if (e.key === 'Enter') onSearch();
    };

    return (
        <div className="search-wrap">
            <input
                type="text"
                placeholder="Search documents… (press Enter)"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                onKeyDown={handleKey}
                id="search-input"
            />
            <button
                className="btn btn-primary"
                onClick={onSearch}
                disabled={loading || !value.trim()}
                id="search-btn"
            >
                {loading ? <span className="spinner" /> : '🔍'} Search
            </button>
        </div>
    );
}
