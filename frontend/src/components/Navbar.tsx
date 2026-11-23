import { NavLink } from "react-router";
import { useAuth } from "../auth/AuthContext";

export default function Navbar() {
  const { token, email, isAdmin, logout } = useAuth();
  return (
    <nav className="navbar">
      <span className="brand">LinkPulse</span>
      {token ? (
        <>
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/links">My Links</NavLink>
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
          <NavLink to="/status">Status</NavLink>
          <span className="spacer" />
          <span className="nav-email">{email}</span>
          <button type="button" onClick={logout}>
            Logout
          </button>
        </>
      ) : (
        <>
          <NavLink to="/status">Status</NavLink>
          <span className="spacer" />
          <NavLink to="/login">Login</NavLink>
          <NavLink to="/register">Register</NavLink>
        </>
      )}
    </nav>
  );
}
