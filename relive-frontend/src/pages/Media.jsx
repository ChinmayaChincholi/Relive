import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMyMedia, getImageUrl } from "../services/mediaService";

export default function Media() {

    const [mediaList, setMediaList] = useState([]);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchMedia = async () => {
            try {
                const data = await getMyMedia();
                setMediaList(data);
            } catch (err) {
                console.error("Error fetching media:", err);
            }
        };
        fetchMedia();
    }, []);

    return (
        <div style={{ padding: 40 }}>
            <h2>Imported Media</h2>

            {mediaList.length === 0 ? (
                <p>No media imported yet.</p>
            ) : (
                <div style={{ display: "flex", flexWrap: "wrap", gap: 16 }}>
                    {mediaList.map((item) => (
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

            <br />
            <button onClick={() => navigate("/dashboard")}>Back to Dashboard</button>
        </div>
    );
}