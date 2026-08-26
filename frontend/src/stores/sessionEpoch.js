export class SessionChangedError extends Error {
  constructor() {
    super('登录状态已变化')
    this.name = 'SessionChangedError'
  }
}

export class SessionEpochGuard {
  #value = 0

  capture() {
    return this.#value
  }

  invalidate() {
    this.#value += 1
  }

  assertCurrent(expected) {
    if (expected !== this.#value) {
      throw new SessionChangedError()
    }
  }
}
