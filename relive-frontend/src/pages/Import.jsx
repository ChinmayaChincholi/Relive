import { useRef, useState } from "react";
import api from "../api/api";

export default function Import() {
  const fileInputRef = useRef();
  const [message, setMessage] = useState("");
  const [uploading, setUploading] = useState(false);

  const handleClick = () => {
    fileInputRef.current.click();
  };

  const handleFiles = async (event) => {
    const files = event.target.files;

    if (!files.length) return;

    const formData = new FormData();
    for (let file of files) {
      formData.append("files", file);
    }

    try {
      setUploading(true);
      setMessage("Uploading...");

      await api.post("/media/upload-folder", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      setMessage("Import started successfully.");
    } catch (err) {
      setMessage("Import failed.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div style={{ padding: 40, textAlign: "center" }}>
      <h2>Import Media</h2>

      <button
        onClick={handleClick}
        style={{
          fontSize: 40,
          padding: 20,
          borderRadius: "50%",
          width: 100,
          height: 100,
          cursor: "pointer",
        }}
      >
        +
      </button>

      <input
        type="file"
        multiple
        style={{ display: "none" }}
        ref={fileInputRef}
        onChange={handleFiles}
      />

      <p style={{ marginTop: 20 }}>{message}</p>
    </div>
  );
}