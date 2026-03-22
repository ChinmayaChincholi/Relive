import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { searchNatural, getImageUrl } from "../services/mediaService";

export default function Ask() {

    const [query, setQuery] = useState("");
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSearch = async () => {
        if (!query.trim()) return;
        try {
            setLoading(true);
            const data = await searchNatural(query);
            setResults(data);
        } catch (err) {
            console.error("Search error:", err);
            alert("Search failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ padding: 40 }}>
            <h2>Ask Relive</h2>

            <input
                type="text"
                placeholder='e.g. "Rahul at beach" or "photos from 2023 at night"'
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") handleSearch(); }}
                style={{ width: "400px", padding: "8px" }}
            />
            <button
                onClick={handleSearch}
                disabled={loading}
                style={{ marginLeft: 10, padding: "8px 16px" }}
            >
                {loading ? "Searching..." : "Search"}
            </button>

            <div style={{ marginTop: 30 }}>
                {results.length === 0 ? (
                    <p>No results yet.</p>
                ) : (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 16 }}>
                        {results.map((item) => (
                            <div
                                key={item.id}
                                style={{
                                    border: "1px solid #444",
                                    borderRadius: 10,
                                    overflow: "hidden",
                                    width: 200,
                                    flexShrink: 0,
                                }}
                            >
                                <img
                                    src={getImageUrl(item.id)}
                                    alt={item.fileName}
                                    style={{ width: "100%", height: 160, objectFit: "cover", display: "block" }}
                                />
                                <div style={{ padding: 10, fontSize: 13 }}>
                                    <p style={{ margin: "4px 0", fontWeight: "bold", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                        {item.fileName}
                                    </p>
                                    <p style={{ margin: "2px 0", color: "#aaa" }}>{item.sceneCaption || "No caption"}</p>
                                    <p style={{ margin: "2px 0" }}>👤 {item.faceCount ?? 0} faces · {item.eventType || "N/A"}</p>
                                    <p style={{ margin: "2px 0" }}>📅 {item.dateTaken ? new Date(item.dateTaken).toLocaleDateString() : "No date"}</p>
                                    <p style={{ margin: "2px 0" }}>📍 {item.location || "No GPS"}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <br />
            <button onClick={() => navigate("/dashboard")}>Back to Dashboard</button>
        </div>
    );
}