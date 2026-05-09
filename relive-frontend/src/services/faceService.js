import api from "../api/api";

export const getPeople = async () => {
  const res = await api.get("/faces/people");
  if (!res.data.success) throw new Error(res.data.message);
  return res.data.data;
};

export const namePerson = async (personId, name) => {
  const res = await api.post(`/faces/name/${personId}`, { name });
  if (!res.data.success) throw new Error(res.data.message);
  return res.data;
};

export const getPhotosForPerson = async (personId) => {
  const res = await api.get(`/faces/person/${personId}/photos`);
  if (!res.data.success) throw new Error(res.data.message);
  return res.data.data;
};

export const reprocessAll = async () => {
  const res = await api.post("/media/reprocess-all");
  if (!res.data.success) throw new Error(res.data.message);
  return res.data.message;
};