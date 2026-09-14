import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Home from './pages/Home';
import Import from './pages/Import';
import Ask from './pages/Ask';
import Media from './pages/Media';
import MediaDetail from './pages/MediaDetail';
import Faces from './pages/Faces';
import PersonPhotos from './pages/PersonPhotos';
import { AskSearchProvider } from './context/AskSearchContext';

function App() {
  return (
    <AskSearchProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/"                    element={<Navigate to="/home" replace />} />
          <Route path="/home"                element={<Home />} />
          <Route path="/import"              element={<Import />} />
          <Route path="/ask"                 element={<Ask />} />
          <Route path="/media"               element={<Media />} />
          <Route path="/media/:id"           element={<MediaDetail />} />
          <Route path="/faces"               element={<Faces />} />
          <Route path="/faces/person/:id"    element={<PersonPhotos />} />
          <Route path="*"                    element={<Navigate to="/home" replace />} />
        </Routes>
      </BrowserRouter>
    </AskSearchProvider>
  );
}

export default App;