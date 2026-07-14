import React, { useEffect, useMemo, useState } from 'react'
import { downloadInvoiceUrl, generateInvoices, getInvoice, getInvoices } from './api.js'

const MONTHS = [
  { value: 1, label: 'Януари' },
  { value: 2, label: 'Февруари' },
  { value: 3, label: 'Март' },
  { value: 4, label: 'Април' },
  { value: 5, label: 'Май' },
  { value: 6, label: 'Юни' },
  { value: 7, label: 'Юли' },
  { value: 8, label: 'Август' },
  { value: 9, label: 'Септември' },
  { value: 10, label: 'Октомври' },
  { value: 11, label: 'Ноември' },
  { value: 12, label: 'Декември' },
]

const DEFAULT_YEAR = 2024
const DEFAULT_MONTH = 3

const currencyFormatter = new Intl.NumberFormat('bg-BG', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const quantityFormatter = new Intl.NumberFormat('bg-BG', {
  minimumFractionDigits: 3,
  maximumFractionDigits: 3,
})

function App() {
  const currentDate = useMemo(() => new Date(), [])
  const [year, setYear] = useState(DEFAULT_YEAR)
  const [month, setMonth] = useState(DEFAULT_MONTH)
  const [invoices, setInvoices] = useState([])
  const [selectedInvoice, setSelectedInvoice] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isPreviewLoading, setIsPreviewLoading] = useState(false)
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)

  const years = useMemo(() => {
    const currentYear = currentDate.getFullYear()
    return Array.from(
      new Set([DEFAULT_YEAR, ...Array.from({ length: 7 }, (_, index) => currentYear - 3 + index)]),
    ).sort((left, right) => left - right)
  }, [currentDate])

  useEffect(() => {
    loadInvoices()
  }, [])

  async function loadInvoices() {
    setIsLoading(true)
    setError(null)

    try {
      setInvoices(await getInvoices())
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setIsLoading(false)
    }
  }

  async function handleGenerate(event) {
    event.preventDefault()
    setIsGenerating(true)
    setMessage(null)
    setError(null)

    try {
      const response = await generateInvoices(Number(year), Number(month))
      setMessage(`Генерирани фактури: ${response.generatedCount}`)
      await loadInvoices()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setIsGenerating(false)
    }
  }

  async function handlePreview(documentNumber) {
    setIsPreviewLoading(true)
    setError(null)

    try {
      setSelectedInvoice(await getInvoice(documentNumber))
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setIsPreviewLoading(false)
    }
  }

  function handleDownload(documentNumber) {
    window.location.assign(downloadInvoiceUrl(documentNumber))
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Mini Billing</p>
          <h1>Фактуриране</h1>
        </div>
        <div className="user-badge">
          <span>Logged in</span>
          <strong>Billing Administrator</strong>
        </div>
      </header>

      <main className="content">
        <section className="toolbar" aria-label="Generate invoices">
          <form className="period-form" onSubmit={handleGenerate}>
            <label>
              <span>Година</span>
              <select value={year} onChange={(event) => setYear(event.target.value)}>
                {years.map((yearOption) => (
                  <option key={yearOption} value={yearOption}>
                    {yearOption}
                  </option>
                ))}
              </select>
            </label>

            <label>
              <span>Месец</span>
              <select value={month} onChange={(event) => setMonth(event.target.value)}>
                {MONTHS.map((monthOption) => (
                  <option key={monthOption.value} value={monthOption.value}>
                    {monthOption.label}
                  </option>
                ))}
              </select>
            </label>

            <button type="submit" disabled={isGenerating}>
              {isGenerating ? 'Генериране...' : 'Генерирай фактури'}
            </button>
          </form>

          <div className="status-area" role="status" aria-live="polite">
            {message && <p className="notice success">{message}</p>}
            {error && <p className="notice error">{error}</p>}
          </div>
        </section>

        <section className="table-section">
          <div className="section-heading">
            <div>
              <h2>Записани фактури</h2>
              <p>{isLoading ? 'Зареждане...' : `${invoices.length} фактури`}</p>
            </div>
            <button className="secondary" type="button" onClick={loadInvoices} disabled={isLoading}>
              Обнови
            </button>
          </div>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Document No.</th>
                  <th>Consumer</th>
                  <th>Reference</th>
                  <th>Document Date</th>
                  <th>Total Amount</th>
                  <th aria-label="Actions"></th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((invoice) => (
                  <tr key={invoice.documentNumber}>
                    <td>{invoice.documentNumber}</td>
                    <td>{invoice.consumer}</td>
                    <td>{invoice.reference}</td>
                    <td>{formatDate(invoice.documentDate)}</td>
                    <td>{formatAmount(invoice.totalAmount)}</td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="secondary"
                          type="button"
                          onClick={() => handlePreview(invoice.documentNumber)}
                          disabled={isPreviewLoading}
                        >
                          Преглед
                        </button>
                        <button
                          className="secondary"
                          type="button"
                          onClick={() => handleDownload(invoice.documentNumber)}
                        >
                          Свали JSON
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}

                {!isLoading && invoices.length === 0 && (
                  <tr>
                    <td colSpan="6" className="empty-state">
                      Няма записани фактури.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {selectedInvoice && (
        <InvoicePreview invoice={selectedInvoice} onClose={() => setSelectedInvoice(null)} />
      )}
    </div>
  )
}

function InvoicePreview({ invoice, onClose }) {
  return (
    <div className="modal-backdrop" role="presentation">
      <aside className="invoice-panel" role="dialog" aria-modal="true" aria-labelledby="invoice-title">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Invoice</p>
            <h2 id="invoice-title">{invoice.documentNumber}</h2>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Close preview">
            ×
          </button>
        </div>

        <dl className="details-grid">
          <div>
            <dt>Consumer</dt>
            <dd>{invoice.consumer}</dd>
          </div>
          <div>
            <dt>Reference</dt>
            <dd>{invoice.reference}</dd>
          </div>
          <div>
            <dt>Document Date</dt>
            <dd>{formatDate(invoice.documentDate)}</dd>
          </div>
          <div>
            <dt>Total Amount</dt>
            <dd>{formatAmount(invoice.totalAmount)}</dd>
          </div>
        </dl>

        <div className="lines-list">
          <h3>Invoice lines</h3>
          {invoice.lines.map((line) => (
            <article key={line.index} className="line-item">
              <div className="line-top">
                <strong>
                  {line.index}. {line.product}
                </strong>
                <span>{formatAmount(line.amount)}</span>
              </div>
              <dl>
                <div>
                  <dt>Quantity</dt>
                  <dd>{quantityFormatter.format(Number(line.quantity))}</dd>
                </div>
                <div>
                  <dt>Price</dt>
                  <dd>{formatAmount(line.price)}</dd>
                </div>
                <div>
                  <dt>Price list</dt>
                  <dd>{line.priceList}</dd>
                </div>
                <div>
                  <dt>Line start</dt>
                  <dd>{formatDate(line.lineStart)}</dd>
                </div>
                <div>
                  <dt>Line end</dt>
                  <dd>{formatDate(line.lineEnd)}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      </aside>
    </div>
  )
}

function formatAmount(value) {
  return currencyFormatter.format(Number(value))
}

function formatDate(value) {
  if (!value) {
    return '—'
  }

  return new Intl.DateTimeFormat('bg-BG', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default App
