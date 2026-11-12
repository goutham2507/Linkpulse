import { Navigate, Outlet } from "react-router";
import { useAuth } from "./AuthContext";

// UX gate only: hides pages from logged-out users. Real enforcement happens in the backend.
export default function ProtectedRoute() {
  const { token } = useAuth();
  return token ? <Outlet /> : <Navigate to="/login" replace />;
}
