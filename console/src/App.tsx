import { Link, Route, Routes } from "react-router-dom";
import { JobsPage } from "./pages/JobsPage";
import { JobDetailPage } from "./pages/JobDetailPage";

export default function App() {
  return (
    <main>
      <h1>
        <Link to="/">TrainQueue</Link>
      </h1>
      <Routes>
        <Route index element={<JobsPage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
      </Routes>
    </main>
  );
}
