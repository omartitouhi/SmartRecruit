import { useLocation } from 'react-router-dom';

/** Shows where the router ended up, so tests can assert redirects. */
export function LocationProbe() {
  const { pathname, search, state } = useLocation();
  return (
    <div>
      <span data-testid="location">{pathname + search}</span>
      <span data-testid="state">{JSON.stringify(state)}</span>
    </div>
  );
}
