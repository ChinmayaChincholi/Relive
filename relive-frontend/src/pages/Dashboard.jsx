import { useNavigate } from "react-router-dom";

export default function Dashboard() {
    const navigate = useNavigate();

    const handleLogout = () => {
        localStorage.removeItem("token");
        navigate("/");
    };

    return (
        <div style={{ padding: 40 }}>
            <h1>Relive Dashboard</h1>

            <div style={{ marginTop: 30, display: "flex", flexWrap: "wrap", gap: 15 }}>
                <button style={{ padding: "10px 20px" }} onClick={() => navigate("/import")}>
                    Import Media
                </button>
                <button style={{ padding: "10px 20px" }} onClick={() => navigate("/ask")}>
                    Ask
                </button>
                <button style={{ padding: "10px 20px" }} onClick={() => navigate("/media")}>
                    Imported Media
                </button>
                <button style={{ padding: "10px 20px" }} onClick={() => navigate("/faces")}>
                    Your People
                </button>
            </div>

            <div style={{ marginTop: 40 }}>
                <button onClick={handleLogout}>Logout</button>
            </div>
        </div>
    );
}