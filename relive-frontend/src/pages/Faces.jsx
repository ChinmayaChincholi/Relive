import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getPeople, namePerson, getPhotosForPerson } from "../services/faceService";
import { getImageUrl, getFaceCropUrl } from "../services/mediaService";

export default function Faces() {

    const [people, setPeople] = useState([]);
    const [loading, setLoading] = useState(true);
    const [nameInputs, setNameInputs] = useState({});
    const [savingId, setSavingId] = useState(null);
    const [selectedPerson, setSelectedPerson] = useState(null);
    const [personPhotos, setPersonPhotos] = useState([]);
    const navigate = useNavigate();

    const fetchPeople = async () => {
        try {
            setLoading(true);
            const data = await getPeople();
            setPeople(data);
        } catch (err) {
            console.error("Error fetching people:", err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPeople();
    }, []);

    const handleNameChange = (personId, value) => {
        setNameInputs(prev => ({ ...prev, [personId]: value }));
    };

    const handleNameSubmit = async (personId) => {
        const name = nameInputs[personId]?.trim();
        if (!name) return;

        try {
            setSavingId(personId);
            await namePerson(personId, name);

            // Update the name locally without re-fetching/re-clustering
            setPeople(prev => prev.map(p =>
                p.personId === personId ? { ...p, name } : p
            ));

            // Clear the input after saving
            setNameInputs(prev => ({ ...prev, [personId]: "" }));

            // Update selected person if it's the one being named
            if (selectedPerson?.personId === personId) {
                setSelectedPerson(prev => ({ ...prev, name }));
            }

        } catch (err) {
            alert("Failed to save name");
        } finally {
            setSavingId(null);
        }
    };

    const handlePersonClick = async (person) => {
        if (selectedPerson?.personId === person.personId) {
            setSelectedPerson(null);
            setPersonPhotos([]);
            return;
        }
        setSelectedPerson(person);
        try {
            const photos = await getPhotosForPerson(person.personId);
            setPersonPhotos(photos);
        } catch (err) {
            console.error("Error fetching person photos:", err);
        }
    };

    return (
        <div style={{ padding: 40 }}>

            <h2>Your People</h2>
            <p style={{ color: "#aaa" }}>
                Faces detected across your photos are grouped here.
                Click a group to see all photos. Name them to search by name.
            </p>

            <div style={{ marginBottom: 20 }}>
                <button onClick={fetchPeople}>Refresh Groups</button>
            </div>

            {loading ? (
                <p>Detecting and grouping faces...</p>
            ) : people.length === 0 ? (
                <p>No faces detected yet. Make sure your photos have finished processing.</p>
            ) : (
                <div style={{ display: "flex", flexWrap: "wrap", gap: 20 }}>
                    {people.map((person) => (
                        <div
                            key={person.personId}
                            style={{
                                border: selectedPerson?.personId === person.personId
                                    ? "2px solid #646cff"
                                    : "1px solid #444",
                                borderRadius: 12,
                                padding: 15,
                                width: 180,
                                textAlign: "center",
                            }}
                        >
                            <div
                                onClick={() => handlePersonClick(person)}
                                style={{ cursor: "pointer" }}
                            >
                                <img
                                    src={getFaceCropUrl(person.representativeCrop)}
                                    alt="face"
                                    style={{
                                        width: 100,
                                        height: 100,
                                        borderRadius: "50%",
                                        objectFit: "cover",
                                        display: "block",
                                        margin: "0 auto 10px",
                                    }}
                                />
                                <p style={{
                                    margin: "4px 0",
                                    fontWeight: "bold",
                                    color: person.name ? "#fff" : "#aaa"
                                }}>
                                    {person.name || "Unknown"}
                                </p>
                                <p style={{ margin: 0, fontSize: 12, color: "#aaa" }}>
                                    {person.mediaIds.length} photo{person.mediaIds.length !== 1 ? "s" : ""}
                                </p>
                            </div>

                            <div
                                style={{ marginTop: 10 }}
                                onClick={(e) => e.stopPropagation()}
                            >
                                <input
                                    type="text"
                                    placeholder={person.name ? `Rename "${person.name}"` : "Add name..."}
                                    value={nameInputs[person.personId] || ""}
                                    onChange={(e) => handleNameChange(person.personId, e.target.value)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") handleNameSubmit(person.personId);
                                    }}
                                    style={{
                                        width: "100%",
                                        padding: "4px 6px",
                                        fontSize: 13,
                                        boxSizing: "border-box"
                                    }}
                                />
                                <button
                                    onClick={() => handleNameSubmit(person.personId)}
                                    disabled={savingId === person.personId}
                                    style={{
                                        marginTop: 5,
                                        width: "100%",
                                        padding: "4px",
                                        fontSize: 12
                                    }}
                                >
                                    {savingId === person.personId ? "Saving..." : "Save Name"}
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {selectedPerson && (
                <div style={{ marginTop: 40 }}>
                    <h3>
                        Photos of {selectedPerson.name || "Unknown Person"}
                        <span style={{ fontSize: 14, color: "#aaa", marginLeft: 10 }}>
                            (click person again to close)
                        </span>
                    </h3>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
                        {personPhotos.map((photo) => (
                            <img
                                key={photo.id}
                                src={getImageUrl(photo.id)}
                                alt={photo.fileName}
                                style={{
                                    width: 160,
                                    height: 160,
                                    objectFit: "cover",
                                    borderRadius: 8,
                                    border: "1px solid #444",
                                }}
                            />
                        ))}
                    </div>
                </div>
            )}

            <br />
            <button onClick={() => navigate("/dashboard")}>Back to Dashboard</button>
        </div>
    );
}