export function formatMoney(value) {
  if (!Number.isFinite(Number(value))) {
    return '—'
  }

  return new Intl.NumberFormat('bg-BG', {
    style: 'currency',
    currency: 'BGN',
    currencyDisplay: 'code',
    minimumFractionDigits: 2,
  }).format(Number(value))
}

export function formatNumber(value, options = {}) {
  if (!Number.isFinite(Number(value))) {
    return '—'
  }

  return new Intl.NumberFormat('bg-BG', options).format(Number(value))
}

export function formatMonthYear(month, year, months = []) {
  const monthLabel = months.find((item) => item.value === Number(month))?.label
  return monthLabel && year ? `${monthLabel} ${year}` : '—'
}

export function formatDateTime(value) {
  if (!value) {
    return '—'
  }

  return new Intl.DateTimeFormat('bg-BG', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatQuantity(value) {
  return formatNumber(value, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
