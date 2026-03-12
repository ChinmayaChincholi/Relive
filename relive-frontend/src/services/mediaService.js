import api from "../api/api";

export const uploadFolder = async (formData) => {

  const res = await api.post("/media/upload-folder", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });

  if (!res.data.success) {
    throw new Error(res.data.message);
  }

  return res.data.message;
};

export const getMyMedia = async () => {

  const res = await api.get("/media/my");

  if (!res.data.success) {
    throw new Error(res.data.message);
  }

  return res.data.data; // return the actual media list

};

export const searchNatural = async (query) => {

  const res = await api.get("/media/search-natural", {
    params: { query },
  });

  if (!res.data.success) {
    throw new Error(res.data.message);
  }

  return res.data.data;

};