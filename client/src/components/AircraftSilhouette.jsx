import React from "react";

function Hotspot({ x, y, label, level = "medium" }) {
  return (
    <g className="hotspot-group">
      <circle
        cx={x}
        cy={y}
        r="11"
        className={`aircraft-hotspot ${level}`}
      />

      <text
        x={x}
        y={y + 4}
        textAnchor="middle"
        className="aircraft-hotspot-text"
      >
        !
      </text>

      <title>{label}</title>
    </g>
  );
}

export default function AircraftSilhouette({ areas = {} }) {
  return (
    <div className="aircraft-silhouette-card">
      <svg
        viewBox="0 0 700 300"
        className="aircraft-silhouette"
        role="img"
        aria-label="Aircraft maintenance condition graphic"
      >
        {/* MLG LH */}
        <rect
          x="255"
          y="170"
          width="75"
          height="20"
          rx="9"
          className="aircraft-engine"
        />

        {/* MLG LH */}
        <rect
          x="255"
          y="109"
          width="75"
          height="20"
          rx="9"
          className="aircraft-engine"
        />

        {/* ENGINE 1 */}
        <rect
          x="355"
          y="235"
          width="45"
          height="20"
          rx="9"
          className="aircraft-engine"
        />

        {/* ENGINE 2 */}
        <rect
          x="330"
          y="200"
          width="45"
          height="20"
          rx="9"
          className="aircraft-engine"
        />

        {/* ENGINE 3 */}
        <rect
          x="320"
          y="85"
          width="45"
          height="20"
          rx="9"
          className="aircraft-engine"
        />

        {/* ENGINE 4 */}
        <rect
          x="355"
          y="50"
          width="45"
          height="20"
          rx="9"
          className="aircraft-engine"
        />

        {/* MAIN WINGS */}
        <polygon
          points="
            300,130
            400,25
            450,25
            365,130
          "
          className="aircraft-wing"
        />

        <polygon
          points="
            300,170
            400,265
            450,265
            365,170
          "
          className="aircraft-wing"
        />

        {/* HORIZONTAL TAIL */}
        <polygon
          points="
            505,132
            575,95
            615,98
            545,142
          "
          className="aircraft-tail"
        />

        <polygon
          points="
            505,168
            575,205
            615,202
            545,158
          "
          className="aircraft-tail"
        />

        {/* FUSELAGE */}
        <path
          d="
            M 78 150

            C 90 136, 120 126, 165 122

            C 190 120, 220 120, 255 120

            L 435 120

            C 470 120, 500 126, 525 136

            L 620 143

            L 620 157

            L 525 164

            C 500 174, 470 180, 435 180

            L 255 180

            C 220 180, 190 180, 165 178

            C 120 174, 90 164, 78 150

            Z
          "
          className="aircraft-body"
        />

        {/* VERTICAL STABILIZER */}
        <polygon
          points="
            555,145
            595,120
            620,120
            588,145
          "
          className="aircraft-tail"
        />


        {/* NOSE HOTSPOT */}
        {areas.nose && (
          <Hotspot
            x={70}
            y={145}
            label="Nose / cockpit"
            level={areas.nose}
          />
        )}

        {/* FUSELAGE HOTSPOT */}
        {areas.body && (
          <Hotspot
            x={400}
            y={150}
            label="Fuselage"
            level={areas.body}
          />
        )}

        {/* ENGINE HOTSPOT */}
        {areas.engine && (
          <Hotspot
            x={322}
            y={75}
            label="Engine system"
            level={areas.engine}
          />
        )}

        {/* WING HOTSPOT */}
        {areas.wing && (
          <Hotspot
            x={395}
            y={240}
            label="Wing / flight control"
            level={areas.wing}
          />
        )}

        {/* LANDING GEAR / HYDRAULICS HOTSPOT */}
        {areas.gear && (
          <Hotspot
            x={262}
            y={198}
            label="Landing gear / brakes / hydraulics"
            level={areas.gear}
          />
        )}

        {/* TAIL HOTSPOT */}
        {areas.tail && (
          <Hotspot
            x={553}
            y={150}
            label="Tail / empennage"
            level={areas.tail}
          />
        )}
      </svg>

      <div className="aircraft-legend">
        <div>
          <span className="legend-dot medium" />
          Open maintenance area
        </div>

        <div>
          <span className="legend-dot high" />
          Red X / high-severity area
        </div>
      </div>
    </div>
  );
}