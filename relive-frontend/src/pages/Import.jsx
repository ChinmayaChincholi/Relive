import { useRef, useState } from "react";
import { uploadFolder } from "../services/mediaService";

export default function Import() {

  const fileInputRef = useRef();

  const [message, setMessage] = useState("");
  const [uploading, setUploading] = useState(false);

  const handleClick = () => {
    fileInputRef.current.click();
  };

  const handleFiles = async (event) => {

    const files = event.target.files;

    if (!files || files.length === 0) return;

    const formData = new FormData();

    for (let file of files) {
      formData.append("files", file);
    }

    try {

      setUploading(true);
      setMessage("Uploading...");

      const responseMessage = await uploadFolder(formData);

      setMessage(responseMessage);

    } catch (err) {

      console.error("Upload error:", err);

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
        disabled={uploading}
        style={{
          fontSize: 40,
          padding: 20,
          borderRadius: "50%",
          width: 100,
          height: 100,
          cursor: uploading ? "not-allowed" : "pointer",
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

      <p style={{ marginTop: 20 }}>
        {message}
      </p>

    </div>

  );

}