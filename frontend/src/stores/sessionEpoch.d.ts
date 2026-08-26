export class SessionChangedError extends Error {}

export class SessionEpochGuard {
  capture(): number
  invalidate(): void
  assertCurrent(expected: number): void
}
