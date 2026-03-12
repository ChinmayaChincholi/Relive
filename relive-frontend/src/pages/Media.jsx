import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMyMedia } from "../services/mediaService";

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

        mediaList.map((item) => (

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
            <p><strong>Status:</strong> {item.status}</p>
            <p><strong>Caption:</strong> {item.sceneCaption}</p>
            <p><strong>Date Taken:</strong> {item.dateTaken || "N/A"}</p>

          </div>

        ))

      )}

      <br />

      <button onClick={() => navigate("/dashboard")}>
        Back to Dashboard
      </button>

    </div>

  );

}