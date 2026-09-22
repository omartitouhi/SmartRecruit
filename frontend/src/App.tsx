import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/layout/AppLayout";
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage";
import { LoginPage } from "./pages/auth/LoginPage";
import { RegisterPage } from "./pages/auth/RegisterPage";
import { CandidateProfilePage } from "./pages/candidate/CandidateProfilePage";
import { JobSearchPage } from "./pages/candidate/JobSearchPage";
import { MatchScorePage } from "./pages/candidate/MatchScorePage";
import { CandidateRankingPage } from "./pages/recruiter/CandidateRankingPage";
import { CompanyProfilePage } from "./pages/recruiter/CompanyProfilePage";

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/login" replace />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="candidate/profile" element={<CandidateProfilePage />} />
        <Route path="candidate/jobs" element={<JobSearchPage />} />
        <Route path="candidate/match/:jobId" element={<MatchScorePage />} />
        <Route path="recruiter/company" element={<CompanyProfilePage />} />
        <Route path="recruiter/jobs/:jobId/candidates" element={<CandidateRankingPage />} />
        <Route path="admin" element={<AdminDashboardPage />} />
      </Route>
    </Routes>
  );
}

export default App;
