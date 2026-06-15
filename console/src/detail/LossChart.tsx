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
  return (
    <div style={{ width: "100%", height: 260 }}>
      <ResponsiveContainer>
        <LineChart data={points}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="epoch" />
          <YAxis domain={[0, "auto"]} />
          <Tooltip />
          <Line type="monotone" dataKey="loss" stroke="#2563eb" dot={false} isAnimationActive />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
