import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Welcome from './pages/Welcome';
import HowItWorks from './pages/HowItWorks';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import Import from './pages/Import';
import Ask from './pages/Ask';
import Media from './pages/Media';
import Faces from './pages/Faces';

function PrivateRoute({ children }) {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/" />;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Welcome />} />
        <Route path="/how-it-works" element={<HowItWorks />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/home" element={<PrivateRoute><Home /></PrivateRoute>} />
        <Route path="/import" element={<PrivateRoute><Import /></PrivateRoute>} />
        <Route path="/ask" element={<PrivateRoute><Ask /></PrivateRoute>} />
        <Route path="/media" element={<PrivateRoute><Media /></PrivateRoute>} />
        <Route path="/faces" element={<PrivateRoute><Faces /></PrivateRoute>} />
        <Route path="/dashboard" element={<Navigate to="/home" />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;