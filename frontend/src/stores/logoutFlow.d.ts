export function executeLogout(
  requestLogout: () => Promise<unknown>,
  clearSession: () => void
): Promise<void>
