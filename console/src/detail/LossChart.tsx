import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { MetricPoint } from "./detailReducer";

// Lazily imported so recharts lands in its own chunk (keeps the main bundle small).
export default function LossChart({ points }: { points: MetricPoint[] }) {
  const axis = "#8b8fa0";
  const grid = "rgba(255,255,255,0.07)";
  return (
    <div style={{ width: "100%", height: 260 }}>
      <ResponsiveContainer>
        <AreaChart data={points} margin={{ top: 8, right: 12, bottom: 4, left: -8 }}>
          <defs>
            <linearGradient id="lossFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#7c6cff" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#7c6cff" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke={grid} vertical={false} />
          <XAxis
            dataKey="epoch"
            stroke={grid}
            tick={{ fill: axis, fontSize: 12 }}
            tickLine={false}
          />
          <YAxis
            domain={[0, "auto"]}
            stroke={grid}
            tick={{ fill: axis, fontSize: 12 }}
            tickLine={false}
          />
          <Tooltip
            contentStyle={{
              background: "#15171e",
              border: "1px solid rgba(255,255,255,0.12)",
              borderRadius: 10,
              color: "#f1f2f5",
            }}
            labelStyle={{ color: "#a0a4ae" }}
            cursor={{ stroke: "rgba(124,108,255,0.5)" }}
          />
          <Area
            type="monotone"
            dataKey="loss"
            stroke="#9d8bff"
            strokeWidth={2.5}
            fill="url(#lossFill)"
            dot={false}
            isAnimationActive
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
