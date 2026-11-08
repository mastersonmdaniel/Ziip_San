/**
 * P3.39 — cliente da pagina inicial e da agenda (API /api/v1/me/home e
 * /api/v1/me/agenda do HomeController). Somente leitura, como o servidor:
 * nao ha metodo de escrita neste cliente porque a agenda nao cria nem
 * altera evento e a pagina inicial e composicao.
 */
import { api } from '@services/api'

export interface HomeShortcutView {
  code: string
  label: string
  route: string
}

export interface HomeWorkItemView {
  id: string
  type: string
  originModule: string
  dueAt: string | null
  status: string
  recordAddress: string
}

export interface HomeNotificationView {
  id: string
  eventName: string
  category: string
  titleCode: string
  createdAt: string
  read: boolean
}

export interface HomeIndicatorView {
  code: string
  name: string
  unit: string
  value: string
  semaphore: 'GREEN' | 'YELLOW' | 'RED' | 'NO_TARGET'
}

export interface HomePageView {
  profileCode: string
  profileLabel: string
  profileEnabled: boolean
  homeRoute: string
  shortcuts: HomeShortcutView[]
  workItems: HomeWorkItemView[]
  workItemsOverdue: number
  notifications: HomeNotificationView[]
  notificationsUnread: number
  tenantAlerts: { available: boolean; items: { code: string; title: string; recordAddress: string | null }[] }
  indicators: HomeIndicatorView[]
  indicatorsTotal: number
  indicatorsTruncated: boolean
}

export interface AgendaEventView {
  id: string
  tenantId: string
  typeCode: string
  title: string
  startsAt: string
  endsAt: string | null
  sourceObjectType: string
  sourceObjectId: string
  recordAddress: string
}

export interface AgendaTypeView {
  type: { code: string; label: string; originTask: string; sourceModule: string }
  sourceAvailable: boolean
}

export const home = {
  page: (profile: string) =>
    api.get<HomePageView>('/me/home', { params: { profile } }).then((r) => r.data),
  agendaTypes: () =>
    api.get<AgendaTypeView[]>('/me/agenda/types').then((r) => r.data),
  agenda: (from: string, to: string) =>
    api.get<AgendaEventView[]>('/me/agenda', { params: { from, to } }).then((r) => r.data),
}
