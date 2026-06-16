import { Link, Route, Routes } from "react-router-dom";
import { JobsPage } from "./pages/JobsPage";
import { JobDetailPage } from "./pages/JobDetailPage";

export default function App() {
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            <span className="brand-mark">◤</span>
            <span className="brand-name">TrainQueue</span>
            <span className="brand-sub">ML Training Scheduler</span>
          </Link>
        </div>
      </header>
      <main>
        <Routes>
          <Route index element={<JobsPage />} />
          <Route path="/jobs/:id" element={<JobDetailPage />} />
        </Routes>
      </main>
    </>
  );
}
