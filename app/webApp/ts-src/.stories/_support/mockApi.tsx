import type { Decorator } from "@storybook/react";
import { client } from "../../generated/api/requests/client.gen";

type Handler = (pathname: string, req: Request) => unknown | undefined;

// Routes the generated hey-api client through a canned in-memory responder so
// stories for components that fetch on mount render real content offline.
export function mockApi(routes: Record<string, unknown>, dynamic?: Handler): Decorator {
  return function WithMockApi(Story) {
    client.setConfig({
      fetch: async (req: Request) => {
        const pathname = new URL(req.url, "http://sb.local").pathname;
        const body = dynamic?.(pathname, req) ?? routes[pathname];
        const status = body === undefined ? 404 : 200;
        return new Response(status === 404 ? "{}" : JSON.stringify(body), {
          status,
          headers: { "content-type": "application/json" },
        });
      },
    });
    return <Story />;
  };
}
