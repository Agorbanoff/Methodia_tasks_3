import MetricIcon from './ui/MetricIcon.jsx'
import Skeleton from './ui/Skeleton.jsx'

function StatsCards({ stats, isLoading = false }) {
  const cards = [
    {
      label: 'Total invoices',
      value: stats.totalInvoices,
      hint: stats.totalInvoices ? 'Loaded from the current billing view' : 'No invoices loaded yet',
      icon: 'document',
      unavailable: !stats.totalInvoices,
    },
    {
      label: 'Total billed amount',
      value: stats.totalAmount,
      hint: stats.totalInvoices ? 'Sum of loaded invoice totals' : 'Amount appears after generation',
      icon: 'amount',
      unavailable: !stats.totalInvoices,
    },
    {
      label: 'Selected period',
      value: stats.selectedPeriod,
      hint: 'Active invoice filter',
      icon: 'calendar',
    },
    {
      label: 'Last document',
      value: stats.lastDocumentNumber,
      hint: stats.lastDocumentNumber === '—' ? 'Unavailable until invoices exist' : 'Highest loaded document number',
      icon: 'hash',
      unavailable: stats.lastDocumentNumber === '—',
    },
    {
      label: 'Total invoice lines',
      value: stats.totalLines,
      hint: stats.totalLines ? 'Across loaded invoices' : 'No line items loaded yet',
      icon: 'lines',
      unavailable: !stats.totalLines,
    },
    {
      label: 'Average invoice value',
      value: stats.averageInvoiceValue,
      hint: stats.totalInvoices ? 'Calculated from loaded invoices' : 'Requires at least one invoice',
      icon: 'average',
      unavailable: !stats.totalInvoices,
    },
    {
      label: 'Average lines',
      value: stats.averageLinesPerInvoice,
      hint: stats.totalInvoices ? 'Line density per invoice' : 'Requires generated invoice lines',
      icon: 'lines',
      unavailable: !stats.totalInvoices,
    },
  ]

  return (
    <section className="stats-grid kpi-grid" aria-label="Billing statistics">
      {cards.map((card, index) => (
        <article
          className={`stat-card kpi-card ${card.unavailable ? 'is-unavailable' : ''}`.trim()}
          key={card.label}
          style={{ '--stagger-index': index }}
        >
          <div className="kpi-card-top">
            <MetricIcon name={card.icon} />
            <span>{card.label}</span>
          </div>
          {isLoading ? (
            <>
              <Skeleton className="kpi-skeleton-value" />
              <Skeleton className="kpi-skeleton-hint" />
            </>
          ) : (
            <>
              <strong>{card.value}</strong>
              <p>{card.hint}</p>
            </>
          )}
        </article>
      ))}
    </section>
  )
}

export default StatsCards
