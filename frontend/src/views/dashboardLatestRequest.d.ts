export class LatestRequestGuard {
  begin(): number
  isCurrent(generation: number): boolean
}
