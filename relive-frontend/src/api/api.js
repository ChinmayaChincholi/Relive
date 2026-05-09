import axios from "axios";

// All requests go to the local Spring Boot backend.
// No auth headers — this is a local-only application.
const api = axios.create({
  baseURL: "http://localhost:8080",
});

export default api;