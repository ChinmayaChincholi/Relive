import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Home from './pages/Home';
import Import from './pages/Import';
import Ask from './pages/Ask';
import Media from './pages/Media';
import Faces from './pages/Faces';

// No auth — this is a local single-user application.
// The app opens directly into the main UI.

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/"       element={<Navigate to="/home" replace />} />
        <Route path="/home"   element={<Home />} />
        <Route path="/import" element={<Import />} />
        <Route path="/ask"    element={<Ask />} />
        <Route path="/media"  element={<Media />} />
        <Route path="/faces"  element={<Faces />} />
        <Route path="*"       element={<Navigate to="/home" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
