import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'
import type { TeachingSubject } from '@/api/subject'

export interface TrainingProfile {
  id: string
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  identityType?: string | null
  collegeId: string
  assessmentYear: string
  secondDisciplineCode: string
  secondDisciplineName: string
  internalMajorCode?: string | null
  internalMajorName?: string | null
  educationLevel: string
  trainingGoal: string
  internshipOrgMode: string
  internshipLocation: string
  teachingSegment: string
  teachingSubjectId: string
  teachingSubjectCode: string
  teachingSubjectName: string
  interviewOrgMode: string
  abilityTestConclusion?: string | null
  status: string
  statusLabel: string
  locked: number
  firstReviewComment?: string | null
  secondReviewComment?: string | null
}

export interface TrainingPayload {
  studentId: string
  collegeId?: string | null
  assessmentYear: string
  secondDisciplineCode: string
  secondDisciplineName: string
  internalMajorCode?: string | null
  internalMajorName?: string | null
  educationLevel: string
  trainingGoal: string
  internshipOrgMode: string
  internshipLocation: string
  teachingSegment: string
  teachingSubjectCode: string
  interviewOrgMode: string
  abilityTestConclusion?: string | null
}

export interface TrainingOptions {
  trainingGoal: string
  defaultSegment?: string | null
  allowedSegments: string[]
  defaultInternshipLocation?: string | null
  allowedInternshipLocations: string[]
  subjects: TeachingSubject[]
}

export interface TrainingQuery {
  keyword?: string | null
  status?: string | null
  collegeId?: string | null
  assessmentYear?: string | null
}

export interface ReviewPayload {
  action: 'PASS' | 'REJECT' | 'FAIL'
  comment?: string | null
}

export function listTrainingProfiles(query: TrainingQuery = {}) {
  return request.get<unknown, ApiResult<PageResult<TrainingProfile>>>('/training', {
    params: cleanParams({ ...query })
  })
}

export function getTrainingProfile(studentId: string, assessmentYear: string) {
  return request.get<unknown, ApiResult<TrainingProfile>>(`/training/${studentId}`, {
    params: { year: assessmentYear }
  })
}

export function getTrainingOptions(goal: string, segment?: string | null) {
  return request.get<unknown, ApiResult<TrainingOptions>>('/training/options', {
    params: cleanParams({ goal, segment })
  })
}

export function saveTrainingProfile(payload: TrainingPayload) {
  return request.post<unknown, ApiResult<string>>('/training', payload)
}

export function confirmTrainingProfile(payload: TrainingPayload) {
  return request.put<unknown, ApiResult<string>>('/training', payload)
}

export function submitTrainingProfile(id: string) {
  return request.post<unknown, ApiResult<null>>(`/training/${id}/submit`, {})
}

export function firstReviewTrainingProfile(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/training/${id}/first-review`, payload)
}

export function secondReviewTrainingProfile(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/training/${id}/second-review`, payload)
}

function cleanParams(query: Record<string, unknown>) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  return params
}
