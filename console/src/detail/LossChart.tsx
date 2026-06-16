import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { MetricPoint } from "./detailReducer";

// Lazily imported so recharts lands in its own chunk (keeps the main bundle small).
export default function LossChart({ points }: { points: MetricPoint[] }) {
  const axis = "#9aa6cf";
  return (
    <div style={{ width: "100%", height: 260 }}>
      <ResponsiveContainer>
        <LineChart data={points} margin={{ top: 8, right: 12, bottom: 4, left: -8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(124,142,196,0.14)" />
          <XAxis
            dataKey="epoch"
            stroke={axis}
            tick={{ fill: axis, fontSize: 12 }}
            tickLine={{ stroke: "rgba(124,142,196,0.2)" }}
          />
          <YAxis
            domain={[0, "auto"]}
            stroke={axis}
            tick={{ fill: axis, fontSize: 12 }}
            tickLine={{ stroke: "rgba(124,142,196,0.2)" }}
          />
          <Tooltip
            contentStyle={{
              background: "#141a30",
              border: "1px solid rgba(124,142,196,0.28)",
              borderRadius: 9,
              color: "#e6ebff",
            }}
            labelStyle={{ color: "#9aa6cf" }}
            cursor={{ stroke: "rgba(34,211,238,0.4)" }}
          />
          <Line
            type="monotone"
            dataKey="loss"
            stroke="#22d3ee"
            strokeWidth={2}
            dot={false}
            isAnimationActive
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
