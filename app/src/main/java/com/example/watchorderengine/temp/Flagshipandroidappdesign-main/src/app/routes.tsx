import { createBrowserRouter, Navigate, useParams } from "react-router";
import { Layout } from "./components/Layout";
import { Home } from "./pages/Home";
import { Discovery } from "./pages/Discovery";
import { Timeline } from "./pages/Timeline";
import { Detail } from "./pages/Detail";
import { Profile } from "./pages/Profile";
import { Opening } from "./pages/Opening";
import { Login } from "./pages/Login";
import { Settings } from "./pages/Settings";
import { Community } from "./pages/Community";

function KeyedDetail() {
  const { id } = useParams();
  return <Detail key={id} />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Opening,
  },
  {
    path: "/login",
    Component: Login,
  },
  {
    path: "/profile",
    element: <Navigate to="/app/profile" replace />,
  },
  {
    path: "/discovery",
    element: <Navigate to="/app/discovery" replace />,
  },
  {
    path: "/settings",
    element: <Navigate to="/app/settings" replace />,
  },
  {
    path: "/timeline",
    element: <Navigate to="/app/timeline/1" replace />,
  },
  {
    path: "/app",
    Component: Layout,
    children: [
      { index: true, Component: Home },
      { path: "discovery", Component: Discovery },
      { path: "detail/:id", Component: KeyedDetail },
      { path: "timeline/:id", Component: Timeline },
      { path: "profile", Component: Profile },
      { path: "settings", Component: Settings },
      { path: "community", Component: Community },
    ],
  },
  {
    path: "*",
    element: <Navigate to="/app" replace />,
  }
]);