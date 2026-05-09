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

export const getProgress = async () => {
  const res = await api.get("/media/progress");
  if (!res.data.success) throw new Error(res.data.message);
  return res.data.data;
};

// Returns a URL the browser can use directly in an <img> src.
// No token needed — the backend is local and unauthenticated.
export const getImageUrl = (mediaId) =>
  `http://localhost:8080/media/image/${mediaId}`;

export const getFaceCropUrl = (cropPath) =>
  `http://localhost:8080/faces/crop?path=${encodeURIComponent(cropPath)}`;