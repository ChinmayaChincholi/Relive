import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { searchNatural } from "../services/mediaService";

export default function Ask() {

  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);

  const navigate = useNavigate();

  const handleSearch = async () => {

    if (!query.trim()) return;

    try {

      const data = await searchNatural(query);

      setResults(data);

    } catch (err) {

      console.error("Search error:", err);
      alert("Search failed");

    }

  };

  return (

    <div style={{ padding: 40 }}>

      <h2>Ask Relive</h2>

      <input
        type="text"
        placeholder="Type your query..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        style={{ width: "400px", padding: "8px" }}
      />

      <button
        onClick={handleSearch}
        style={{ marginLeft: 10, padding: "8px 16px" }}
      >
        Search
      </button>

      <div style={{ marginTop: 40 }}>

        {results.length === 0 ? (

          <p>No results yet.</p>

        ) : (

          results.map((item) => (

            <div
              key={item.id}
              style={{
                border: "1px solid #444",
                padding: 15,
                marginBottom: 15,
                borderRadius: 8,
              }}
            >

              <p><strong>File:</strong> {item.fileName}</p>
              <p><strong>Caption:</strong> {item.sceneCaption}</p>
              <p><strong>Date Taken:</strong> {item.dateTaken || "N/A"}</p>
              <p><strong>Status:</strong> {item.status}</p>

            </div>

          ))

        )}

      </div>

      <br />

      <button onClick={() => navigate("/dashboard")}>
        Back to Dashboard
      </button>

    </div>

  );

}