"use client";

import React from "react";
import L from "leaflet";
import {
  MapContainer,
  TileLayer,
  Marker,
  useMap,
  useMapEvents,
} from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { PHNOM_PENH } from "@/lib/geo";

export interface LatLng {
  lat: number;
  lng: number;
}

const pinIcon = L.divIcon({
  className: "",
  html: '<div style="font-size:28px;line-height:28px;transform:translate(-50%,-100%)">📍</div>',
  iconSize: [0, 0],
});

function ClickHandler({ onPick }: { onPick: (p: LatLng) => void }) {
  useMapEvents({
    click(e) {
      onPick({ lat: e.latlng.lat, lng: e.latlng.lng });
    },
  });
  return null;
}

function Recenter({ value }: { value: LatLng | null }) {
  const map = useMap();
  React.useEffect(() => {
    if (value) map.setView([value.lat, value.lng], Math.max(map.getZoom(), 16));
  }, [map, value]);
  return null;
}

// The dialog may animate/resize; make sure tiles fill the container.
function InvalidateSize() {
  const map = useMap();
  React.useEffect(() => {
    const t = setTimeout(() => map.invalidateSize(), 150);
    return () => clearTimeout(t);
  }, [map]);
  return null;
}

export default function LocationPicker({
  value,
  onChange,
}: {
  value: LatLng | null;
  onChange: (p: LatLng) => void;
}) {
  return (
    <div data-testid="location-map">
      <MapContainer
        center={value ? [value.lat, value.lng] : PHNOM_PENH}
        zoom={value ? 16 : 12}
        scrollWheelZoom={false}
        className="h-56 sm:h-64 w-full rounded-btn border border-border z-0"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <ClickHandler onPick={onChange} />
        <InvalidateSize />
        <Recenter value={value} />
        {value && (
          <Marker
            position={[value.lat, value.lng]}
            icon={pinIcon}
            draggable
            eventHandlers={{
              dragend(e) {
                const ll = (e.target as L.Marker).getLatLng();
                onChange({ lat: ll.lat, lng: ll.lng });
              },
            }}
          />
        )}
      </MapContainer>
    </div>
  );
}
