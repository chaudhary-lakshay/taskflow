/** Today as an ISO date in UTC, the same calendar day the backend uses for "overdue" and "not in the past". */
export const utcToday = () => new Date().toISOString().slice(0, 10)

/** Overdue = due before today and not DONE. Today is not overdue; no date is never overdue. Mirrors the backend rule. */
export const isOverdue = (dueDate: string | null, status: string, today: string) => dueDate !== null && dueDate < today && status !== 'DONE'
