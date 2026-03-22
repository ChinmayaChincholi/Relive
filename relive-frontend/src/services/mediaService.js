import api from "../api/api";

export const uploadFolder = async (formData) => {
    const res = await api.post("/media/upload-folder", formData, {
        headers: { "Content-Type": "multipart/form-data" },
    });
    if (!res.data.success) throw new Error(res.data.message);
    return res.data.message;
};

export const getMyMedia = async () => {
    const res = await api.get("/media/my");
    if (!res.data.success) throw new Error(res.data.message);
    return res.data.data;
};

export const searchNatural = async (query) => {
    const res = await api.get("/media/search-natural", { params: { query } });
    if (!res.data.success) throw new Error(res.data.message);
    return res.data.data;
};

// Returns a URL that the browser can use directly in an <img> src
export const getImageUrl = (mediaId) => {
    const token = localStorage.getItem("token");
    return `http://localhost:8080/media/image/${mediaId}?token=${token}`;
};

export const getFaceCropUrl = (cropPath) => {
    const token = localStorage.getItem("token");
    return `http://localhost:8080/faces/crop?path=${encodeURIComponent(cropPath)}&token=${token}`;
};