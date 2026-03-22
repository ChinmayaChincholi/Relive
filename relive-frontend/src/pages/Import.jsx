import { useRef, useState } from "react";
import { uploadFolder } from "../services/mediaService";

export default function Import() {

    const fileInputRef = useRef();
    const [message, setMessage] = useState("");
    const [uploading, setUploading] = useState(false);
    const [progress, setProgress] = useState({ current: 0, total: 0 });

    const handleClick = () => {
        fileInputRef.current.click();
    };

    const handleFiles = async (event) => {

        const files = Array.from(event.target.files);

        if (!files || files.length === 0) return;

        try {

            setUploading(true);
            setProgress({ current: 0, total: files.length });

            let skipped = 0;
            let failed = 0;

            // Upload one file at a time instead of all at once
            for (let i = 0; i < files.length; i++) {

                setProgress({ current: i + 1, total: files.length });
                setMessage(`Uploading ${i + 1} of ${files.length}: ${files[i].name}`);

                try {

                    const formData = new FormData();
                    formData.append("files", files[i]);

                    const result = await uploadFolder(formData);

                    if (result && result.includes("already exists")) {
                        skipped++;
                    }

                } catch (err) {
                    console.error(`Failed to upload ${files[i].name}:`, err);
                    failed++;
                }
            }

            // Summary message
            const uploaded = files.length - skipped - failed;
            let summary = `Done! ${uploaded} uploaded`;
            if (skipped > 0) summary += `, ${skipped} skipped (already exist)`;
            if (failed > 0) summary += `, ${failed} failed`;
            setMessage(summary + ". Processing started in background.");

        } finally {
            setUploading(false);
            setProgress({ current: 0, total: 0 });
            // Reset file input so same files can be selected again
            fileInputRef.current.value = "";
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
                accept="image/*"
                style={{ display: "none" }}
                ref={fileInputRef}
                onChange={handleFiles}
            />

            {uploading && progress.total > 0 && (
                <div style={{ marginTop: 20 }}>
                    <div style={{
                        width: "300px",
                        height: 8,
                        backgroundColor: "#333",
                        borderRadius: 4,
                        margin: "0 auto 10px",
                        overflow: "hidden"
                    }}>
                        <div style={{
                            width: `${(progress.current / progress.total) * 100}%`,
                            height: "100%",
                            backgroundColor: "#646cff",
                            borderRadius: 4,
                            transition: "width 0.3s ease"
                        }} />
                    </div>
                    <p style={{ color: "#aaa", fontSize: 13 }}>
                        {progress.current} / {progress.total} files
                    </p>
                </div>
            )}

            <p style={{ marginTop: 20 }}>{message}</p>

        </div>
    );
}