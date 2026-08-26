/**
 * 远端撤销无论成功与否都必须清理本地会话；远端异常继续抛给调用方用于显式告警。
 *
 * @param {() => Promise<unknown>} requestLogout
 * @param {() => void} clearSession
 * @returns {Promise<void>}
 */
export async function executeLogout(requestLogout, clearSession) {
  try {
    await requestLogout()
  } finally {
    clearSession()
  }
}
