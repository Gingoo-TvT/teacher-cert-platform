import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface College {
  id: string
  code: string
  name: string
  sort: number
  status: number
}

export interface CollegePayload {
  code: string
  name: string
  sort?: number | null
  status?: number | null
}

export interface TrainingGoal {
  code: string
  name: string
  sort: number
  status: number
}

export interface Major {
  id: string
  collegeId: string
  collegeName?: string | null
  internalMajorCode: string
  internalMajorName: string
  secondDisciplineCode?: string | null
  secondDisciplineName?: string | null
  pilotScopeFlag: number
  yearVersion: string
  sort: number
  status: number
  trainingGoals: TrainingGoal[]
}

export interface MajorPayload {
  collegeId: string
  internalMajorCode: string
  internalMajorName: string
  secondDisciplineCode?: string | null
  secondDisciplineName?: string | null
  pilotScopeFlag?: number | null
  yearVersion?: string | null
  sort?: number | null
  status?: number | null
}

export interface MajorQuery {
  collegeId?: string | null
  yearVersion?: string | null
  pilotScopeFlag?: number | null
  status?: number | null
  keyword?: string | null
}

export interface TrainingGoalConfig {
  id?: string | null
  trainingGoalCode: string
  trainingGoalName?: string | null
  defaultSegment: string
  defaultSegmentName?: string | null
  allowedSegments: string[]
  defaultInternshipLocation: string
  defaultInternshipLocationName?: string | null
  allowedInternshipLocations: string[]
  status: number
}

export interface TrainingGoalConfigPayload {
  trainingGoalCode: string
  defaultSegment: string
  allowedSegments: string[]
  defaultInternshipLocation: string
  allowedInternshipLocations: string[]
  status?: number | null
}

export function listColleges(keyword?: string | null, status?: number | null) {
  return request.get<unknown, ApiResult<College[]>>('/college', {
    params: cleanParams({ keyword, status })
  })
}

export function createCollege(payload: CollegePayload) {
  return request.post<unknown, ApiResult<string>>('/college', payload)
}

export function updateCollege(id: string, payload: CollegePayload) {
  return request.put<unknown, ApiResult<null>>(`/college/${id}`, payload)
}

export function deleteCollege(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/college/${id}`)
}

export function listMajors(query: MajorQuery = {}) {
  return request.get<unknown, ApiResult<Major[]>>('/major', {
    params: cleanParams({ ...query })
  })
}

export function getMajor(id: string) {
  return request.get<unknown, ApiResult<Major>>(`/major/${id}`)
}

export function createMajor(payload: MajorPayload) {
  return request.post<unknown, ApiResult<string>>('/major', payload)
}

export function updateMajor(id: string, payload: MajorPayload) {
  return request.put<unknown, ApiResult<null>>(`/major/${id}`, payload)
}

export function deleteMajor(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/major/${id}`)
}

export function listMajorTrainingGoals(id: string) {
  return request.get<unknown, ApiResult<TrainingGoal[]>>(`/major/${id}/training-goals`)
}

export function replaceMajorTrainingGoals(id: string, trainingGoalCodes: string[]) {
  return request.put<unknown, ApiResult<null>>(`/major/${id}/training-goals`, { trainingGoalCodes })
}

export function listTrainingGoalConfigs(trainingGoalCode?: string | null) {
  return request.get<unknown, ApiResult<TrainingGoalConfig[]>>('/training-goal-config', {
    params: cleanParams({ trainingGoalCode })
  })
}

export function saveTrainingGoalConfig(payload: TrainingGoalConfigPayload) {
  return request.put<unknown, ApiResult<null>>('/training-goal-config', payload)
}

function cleanParams(query: Record<string, unknown>) {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string') {
      const text = value.trim()
      if (text) params[key] = text
    } else if (typeof value === 'number') {
      params[key] = value
    }
  }
  return params
}
