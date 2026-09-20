import Sidebar from './Sidebar';
import TopBar from './TopBar';

// scrollRef (optional) is attached to the page's scroll container so a page
// (e.g. All Photos) can read/restore its scroll position.
export default function AppLayout({ children, noTopBar = false, scrollRef = null }) {
  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Sidebar />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {!noTopBar && <TopBar />}
        <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto' }}>
          {children}
        </div>
      </div>
    </div>
  );
}