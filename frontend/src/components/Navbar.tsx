import { NavLink } from "react-router";
import { useAuth } from "../auth/AuthContext";

export default function Navbar() {
  const { token, email, isAdmin, logout } = useAuth();
  return (
    <nav className="navbar">
      <span className="brand">LinkPulse</span>
      {token ? (
        <>
          <NavLink to="/links">My Links</NavLink>
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
          <span className="spacer" />
          <span className="nav-email">{email}</span>
          <button type="button" onClick={logout}>
            Logout
          </button>
        </>
      ) : (
        <>
          <span className="spacer" />
          <NavLink to="/login">Login</NavLink>
          <NavLink to="/register">Register</NavLink>
        </>
      )}
    </nav>
  );
}
