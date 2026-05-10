import { useEffect, useState, useRef, useCallback } from 'react';
import AppLayout from '../components/AppLayout';
import { getMyMedia, getImageUrl } from '../services/mediaService';

// Leaflet is installed as a local npm package — zero CDN requests at import time.
// Tile images are fetched from CartoDB (requires internet) OR you can run a local
// tile server. See README for offline tile setup instructions.
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix Leaflet's default icon paths (broken by Vite's asset pipeline)
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

// Parse "City, Region, CC (lat,lon)" stored by Relive's reverse geocoder
function parseLocation(locationStr) {
  if (!locationStr) return null;
  const match = locationStr.match(/\((-?\d+\.?\d*),\s*(-?\d+\.?\d*)\)/);
  if (!match) return null;
  return {
    lat: parseFloat(match[1]),
    lon: parseFloat(match[2]),
    label: locationStr.split('(')[0].trim().replace(/,\s*$/, ''),
  };
}

// Create a custom amber circular marker
function makeMarkerIcon(count, isSelected) {
  const size = isSelected ? 42 : 36;
  const color = isSelected ? '#fbbf24' : '#f59e0b';
  const html = `
    <div style="
      width:${size}px;height:${size}px;
      background:${color};
      border:2.5px solid #fff;
      border-radius:50%;
      display:flex;align-items:center;justify-content:center;
      font-family:Syne,sans-serif;font-size:${count > 99 ? 9 : 11}px;font-weight:800;
      color:#1c1004;
      box-shadow:0 2px 10px rgba(0,0,0,0.5);
      cursor:pointer;
      transition:all 0.15s;
    ">${count}</div>`;
  return L.divIcon({
    html,
    className: '',
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });
}

export default function MapPage() {
  const mapRef = useRef(null);
  const leafletMap = useRef(null);
  const markersRef = useRef({});
  const [media, setMedia] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedLocation, setSelectedLocation] = useState(null);

  useEffect(() => {
    getMyMedia()
      .then(data => {
        setMedia(data.filter(m => m.status === 'COMPLETED' && m.location));
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  // Group media by location label (city name)
  const byLocation = media.reduce((acc, item) => {
    const parsed = parseLocation(item.location);
    if (!parsed) return acc;
    const key = parsed.label;
    if (!acc[key]) acc[key] = { ...parsed, items: [] };
    acc[key].items.push(item);
    return acc;
  }, {});
  const locations = Object.values(byLocation);

  const handleMarkerClick = useCallback((loc) => {
    setSelectedLocation(loc);
    // Highlight selected marker
    Object.entries(markersRef.current).forEach(([label, marker]) => {
      const locData = byLocation[label];
      if (!locData) return;
      marker.setIcon(makeMarkerIcon(locData.items.length, label === loc.label));
    });
  }, [byLocation]);

  // Initialise Leaflet map once
  useEffect(() => {
    if (!mapRef.current || leafletMap.current) return;

    const map = L.map(mapRef.current, {
      center: [20, 0],
      zoom: 2,
      zoomControl: true,
      attributionControl: true,
    });

    // Dark tile layer — CartoDB Dark Matter (no API key needed, free)
    // NOTE: requires internet for tile loading. For fully offline tiles,
    // see README: run a local tile server with mbtiles.
    L.tileLayer(
      'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
      {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> © <a href="https://carto.com/">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 19,
      }
    ).addTo(map);

    leafletMap.current = map;

    return () => {
      map.remove();
      leafletMap.current = null;
      markersRef.current = {};
    };
  }, []);

  // Add/update markers whenever locations change
  useEffect(() => {
    const map = leafletMap.current;
    if (!map || loading) return;

    // Remove all existing markers
    Object.values(markersRef.current).forEach(m => m.remove());
    markersRef.current = {};

    if (locations.length === 0) return;

    locations.forEach(loc => {
      const count = loc.items.length;
      const isSelected = selectedLocation?.label === loc.label;
      const marker = L.marker([loc.lat, loc.lon], {
        icon: makeMarkerIcon(count, isSelected),
      });

      // Tooltip showing place name always visible
      marker.bindTooltip(
        `<div style="font-family:Syne,sans-serif;font-weight:700;font-size:12px;color:#f59e0b;">${loc.label}</div>
         <div style="font-size:10px;color:#94a3b8;margin-top:2px;">${count} photo${count !== 1 ? 's' : ''}</div>`,
        {
          permanent: false,
          direction: 'top',
          offset: [0, -20],
          className: 'relive-tooltip',
        }
      );

      marker.on('click', () => {
        handleMarkerClick(loc);
        // Smooth fly-to with appropriate zoom
        const targetZoom = Math.max(map.getZoom(), 10);
        map.flyTo([loc.lat, loc.lon], targetZoom, { duration: 1.2 });
      });

      marker.addTo(map);
      markersRef.current[loc.label] = marker;
    });

    // If only one location, fly to it automatically
    if (locations.length === 1) {
      map.flyTo([locations[0].lat, locations[0].lon], 10, { duration: 1.5 });
    } else {
      // Fit map to show all markers
      const group = L.featureGroup(Object.values(markersRef.current));
      map.fitBounds(group.getBounds().pad(0.2));
    }
  }, [locations.length, loading]);

  // Update selected marker icon when selection changes
  useEffect(() => {
    Object.entries(markersRef.current).forEach(([label, marker]) => {
      const loc = byLocation[label];
      if (!loc) return;
      marker.setIcon(makeMarkerIcon(loc.items.length, label === selectedLocation?.label));
    });
  }, [selectedLocation]);

  return (
    <AppLayout>
      {/* Leaflet tooltip styles injected globally */}
      <style>{`
        .relive-tooltip {
          background: #0f1520 !important;
          border: 1px solid rgba(245,158,11,0.3) !important;
          border-radius: 8px !important;
          padding: 6px 10px !important;
          box-shadow: 0 4px 16px rgba(0,0,0,0.4) !important;
          color: #f1f5f9 !important;
        }
        .relive-tooltip::before {
          border-top-color: rgba(245,158,11,0.3) !important;
        }
        .leaflet-control-attribution {
          background: rgba(15,21,32,0.8) !important;
          color: #475569 !important;
          font-size: 9px !important;
        }
        .leaflet-control-attribution a { color: #64748b !important; }
        .leaflet-control-zoom a {
          background: #0f1520 !important;
          border-color: rgba(255,255,255,0.1) !important;
          color: #94a3b8 !important;
        }
        .leaflet-control-zoom a:hover {
          background: #141b27 !important;
          color: #f1f5f9 !important;
        }
      `}</style>

      <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>

        {/* Header */}
        <div style={{
          padding: '16px 24px',
          borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          flexShrink: 0,
        }}>
          <div>
            <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800' }}>Map</div>
            <div style={{ fontSize: '12px', color: 'var(--text3)', marginTop: '2px' }}>
              {loading
                ? 'Loading...'
                : `${locations.length} location${locations.length !== 1 ? 's' : ''} · ${media.length} photo${media.length !== 1 ? 's' : ''}`}
            </div>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            {selectedLocation && (
              <>
                <button
                  onClick={() => {
                    if (leafletMap.current) {
                      const group = L.featureGroup(Object.values(markersRef.current));
                      leafletMap.current.fitBounds(group.getBounds().pad(0.2));
                    }
                    setSelectedLocation(null);
                  }}
                  style={{
                    padding: '6px 14px', background: 'var(--surface)',
                    border: '1px solid var(--border)', borderRadius: '8px',
                    fontSize: '12px', color: 'var(--text2)', cursor: 'pointer',
                  }}
                >Show all</button>
                <button
                  onClick={() => setSelectedLocation(null)}
                  style={{
                    padding: '6px 14px', background: 'var(--surface)',
                    border: '1px solid var(--border)', borderRadius: '8px',
                    fontSize: '12px', color: 'var(--text3)', cursor: 'pointer',
                  }}
                >Close ×</button>
              </>
            )}
          </div>
        </div>

        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

          {/* Map */}
          <div style={{ flex: 1, position: 'relative' }}>
            <div ref={mapRef} style={{ width: '100%', height: '100%' }} />

            {!loading && locations.length === 0 && (
              <div style={{
                position: 'absolute', inset: 0, pointerEvents: 'none',
                display: 'flex', flexDirection: 'column',
                alignItems: 'center', justifyContent: 'center',
                color: 'var(--text3)', fontSize: '13px', gap: '10px',
              }}>
                <div style={{ fontSize: '40px' }}>🗺️</div>
                <div>No photos with location data yet.</div>
                <div style={{ fontSize: '11px' }}>Photos need GPS EXIF data to appear on the map.</div>
              </div>
            )}
          </div>

          {/* Side panel */}
          {selectedLocation && (
            <div style={{
              width: '300px', flexShrink: 0,
              borderLeft: '1px solid var(--border)',
              background: 'var(--bg2)',
              overflowY: 'auto',
              padding: '16px',
            }}>
              <div style={{
                fontFamily: 'Syne, sans-serif', fontSize: '14px',
                fontWeight: '700', marginBottom: '4px',
              }}>
                📍 {selectedLocation.label}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text3)', marginBottom: '14px' }}>
                {selectedLocation.items.length} photo{selectedLocation.items.length !== 1 ? 's' : ''}
                {' · '}
                <span style={{ color: '#f59e0b' }}>
                  {selectedLocation.lat.toFixed(4)}°, {selectedLocation.lon.toFixed(4)}°
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                {selectedLocation.items.map(item => (
                  <div key={item.id} style={{
                    borderRadius: '8px', overflow: 'hidden',
                    border: '1px solid var(--border)',
                    background: 'var(--surface)',
                  }}>
                    <div style={{ height: '110px', background: 'var(--bg3)', overflow: 'hidden' }}>
                      <img
                        src={getImageUrl(item.id)}
                        alt={item.fileName}
                        style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                      />
                    </div>
                    {item.dateTaken && (
                      <div style={{ padding: '5px 7px', fontSize: '9px', color: '#f59e0b' }}>
                        {new Date(item.dateTaken).toLocaleDateString()}
                      </div>
                    )}
                    <div style={{
                      padding: '0 7px 5px', fontSize: '9px', color: 'var(--text3)',
                      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    }}>{item.fileName}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}