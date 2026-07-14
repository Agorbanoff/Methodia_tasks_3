const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

async function request(path, options = {}) {
  let response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
      ...options,
    })
  } catch {
    throw new Error('Неуспешна връзка със сървъра. Проверете дали backend приложението работи.')
  }

  if (!response.ok) {
    const message = await readErrorMessage(response)
    throw new Error(message)
  }

  if (response.status === 204) {
    return null
  }

  return response.json()
}

async function readErrorMessage(response) {
  try {
    const data = await response.json()
    return data.detail || data.title || `Заявката завърши с грешка ${response.status}.`
  } catch {
    return `Заявката завърши с грешка ${response.status}.`
  }
}

export function getInvoices() {
  return request('/api/invoices')
}

export function generateInvoices(year, month) {
  return request('/api/invoices/generate', {
    method: 'POST',
    body: JSON.stringify({ year, month }),
  })
}

export function getInvoice(documentNumber) {
  return request(`/api/invoices/${encodeURIComponent(documentNumber)}`)
}

export function downloadInvoiceUrl(documentNumber) {
  return `${API_BASE_URL}/api/invoices/${encodeURIComponent(documentNumber)}/download`
}

