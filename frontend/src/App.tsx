import { useEffect } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { RequireRole } from "./auth/RequireRole";
import { AppLayout } from "./components/layout/AppLayout";
import { PublicLayout } from "./components/layout/PublicLayout";
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage";
import { LoginPage } from "./pages/auth/LoginPage";
import { RegisterPage } from "./pages/auth/RegisterPage";
import { CandidateProfilePage } from "./pages/candidate/CandidateProfilePage";
import { JobSearchPage } from "./pages/candidate/JobSearchPage";
import { MatchScorePage } from "./pages/candidate/MatchScorePage";
import { CandidateRankingPage } from "./pages/recruiter/CandidateRankingPage";
import { CompanyProfilePage } from "./pages/recruiter/CompanyProfilePage";

/** Scroll to the top on page change (not on query-string changes such as filters). */
function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
}

function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<Navigate to="/login" replace />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
        </Route>

        <Route element={<RequireRole roles={["CANDIDATE"]}><AppLayout /></RequireRole>}>
          <Route path="candidate/profile" element={<CandidateProfilePage />} />
          <Route path="candidate/jobs" element={<JobSearchPage />} />
          <Route path="candidate/match/:jobId" element={<MatchScorePage />} />
        </Route>

        <Route element={<RequireRole roles={["RECRUITER"]}><AppLayout /></RequireRole>}>
          <Route path="recruiter/company" element={<CompanyProfilePage />} />
          <Route path="recruiter/jobs/:jobId/candidates" element={<CandidateRankingPage />} />
        </Route>

        <Route element={<RequireRole roles={["ADMIN"]}><AppLayout /></RequireRole>}>
          <Route path="admin" element={<AdminDashboardPage />} />
        </Route>
      </Routes>
    </>
  );
}

export default App;
