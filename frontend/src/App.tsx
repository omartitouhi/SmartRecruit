import { useEffect } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import { RequireRole } from "./auth/RequireRole";
import { AppLayout } from "./components/layout/AppLayout";
import { PublicLayout } from "./components/layout/PublicLayout";
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage";
import { LoginPage } from "./pages/auth/LoginPage";
import { ForgotPasswordPage, ResetPasswordPage } from "./pages/auth/PasswordPages";
import { RegisterPage } from "./pages/auth/RegisterPage";
import { ApplicationsPage } from "./pages/candidate/ApplicationsPage";
import { CandidateDashboardPage } from "./pages/candidate/CandidateDashboardPage";
import { CandidateProfilePage } from "./pages/candidate/CandidateProfilePage";
import { JobDetailPage } from "./pages/candidate/JobDetailPage";
import { JobSearchPage } from "./pages/candidate/JobSearchPage";
import { MatchScorePage } from "./pages/candidate/MatchScorePage";
import { RecommendationsPage } from "./pages/candidate/RecommendationsPage";
import { ForbiddenPage } from "./pages/ForbiddenPage";
import { LandingPage } from "./pages/LandingPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { CandidateRankingPage } from "./pages/recruiter/CandidateRankingPage";
import { CompanyProfilePage } from "./pages/recruiter/CompanyProfilePage";
import { NewJobPage } from "./pages/recruiter/NewJobPage";

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
          <Route index element={<LandingPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />
          <Route path="forbidden" element={<ForbiddenPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        <Route element={<RequireRole roles={["CANDIDATE"]}><AppLayout /></RequireRole>}>
          <Route path="candidate" element={<CandidateDashboardPage />} />
          <Route path="candidate/recommendations" element={<RecommendationsPage />} />
          <Route path="candidate/profile" element={<CandidateProfilePage />} />
          <Route path="candidate/jobs" element={<JobSearchPage />} />
          <Route path="candidate/jobs/:jobId" element={<JobDetailPage />} />
          <Route path="candidate/applications" element={<ApplicationsPage />} />
          <Route path="candidate/match/:jobId" element={<MatchScorePage />} />
        </Route>

        <Route element={<RequireRole roles={["RECRUITER"]}><AppLayout /></RequireRole>}>
          <Route path="recruiter/company" element={<CompanyProfilePage />} />
          <Route path="recruiter/jobs/new" element={<NewJobPage />} />
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
