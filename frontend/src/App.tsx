import { Navigate, Route, Routes } from "react-router";
import { useAuth } from "./auth/AuthContext";
import ProtectedRoute from "./auth/ProtectedRoute";
import Navbar from "./components/Navbar";
import AdminPage from "./pages/AdminPage";
import LinksPage from "./pages/LinksPage";
import LoginPage from "./pages/LoginPage";
import NotFoundPage from "./pages/NotFoundPage";
import RegisterPage from "./pages/RegisterPage";

export default function App() {
  const { token, isAdmin } = useAuth();
  return (
    <>
      <Navbar />
      <main className="container">
        <Routes>
          <Route path="/" element={<Navigate to={token ? "/links" : "/login"} replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/links" element={<LinksPage />} />
            {/* Non-admins see 404; the backend still returns 403 if they call the API directly */}
            <Route path="/admin" element={isAdmin ? <AdminPage /> : <NotFoundPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
    </>
  );
}
