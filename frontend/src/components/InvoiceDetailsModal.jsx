import Button from './ui/Button.jsx'
import Drawer from './ui/Drawer.jsx'
import IconButton from './ui/IconButton.jsx'
import SectionHeader from './ui/SectionHeader.jsx'
import Skeleton from './ui/Skeleton.jsx'
import { formatDateTime, formatMoney, formatQuantity } from '../utils/formatters.js'

function InvoiceDetailsModal({ invoice, selectedPeriod, isLoading, onClose, onDownload }) {
  if (!invoice && !isLoading) {
    return null
  }

  return (
    <Drawer labelledBy="invoice-modal-title" onClose={onClose}>
      {isLoading ? (
        <div className="modal-loading drawer-skeleton" aria-label="Loading invoice details">
          <Skeleton className="drawer-skeleton-title" />
          <div className="drawer-skeleton-grid">
            <Skeleton />
            <Skeleton />
            <Skeleton />
          </div>
          <Skeleton className="drawer-skeleton-table" />
        </div>
      ) : (
        <>
          <div className="modal-header">
            <div>
              <p className="eyebrow">Invoice details</p>
              <h2 id="invoice-modal-title">{invoice.documentNumber}</h2>
              <p>{invoice.consumer}</p>
            </div>
            <IconButton label="Close invoice details" onClick={onClose}>
              ×
            </IconButton>
          </div>

          <div className="modal-summary">
            <SummaryItem label="Consumer" value={invoice.consumer} />
            <SummaryItem label="Reference" value={invoice.reference} />
            <SummaryItem label="Issue date" value={formatDateTime(invoice.documentDate)} />
            <SummaryItem label="Billing period" value={formatPeriod(invoice, selectedPeriod)} />
            <SummaryItem label="Lines" value={invoice.lines?.length ?? 0} />
            <SummaryItem label="Total amount" value={formatMoney(invoice.totalAmount)} accent />
          </div>

          <div className="modal-actions">
            <Button type="button" onClick={() => onDownload(invoice.documentNumber)}>
              Download JSON
            </Button>
          </div>

          <div className="lines-section">
            <SectionHeader
              className="section-title compact"
              title="Invoice lines"
              description={`${invoice.lines?.length || 0} billing line items`}
            />

            <div className="table-wrap lines-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Product</th>
                    <th>Quantity</th>
                    <th>Price</th>
                    <th>Amount</th>
                    <th>Line start</th>
                    <th>Line end</th>
                    <th>Price list</th>
                  </tr>
                </thead>
                <tbody>
                  {(invoice.lines || []).map((line) => (
                    <tr key={line.index}>
                      <td>{line.index}</td>
                      <td>
                        <span className="product-pill">{String(line.product).toUpperCase()}</span>
                      </td>
                      <td>{formatQuantity(line.quantity)}</td>
                      <td>{formatMoney(line.price)}</td>
                      <td className="amount-cell">{formatMoney(line.amount)}</td>
                      <td>{formatDateTime(line.lineStart)}</td>
                      <td>{formatDateTime(line.lineEnd)}</td>
                      <td>{line.priceList}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </Drawer>
  )
}

function SummaryItem({ label, value, accent = false }) {
  return (
    <div className={accent ? 'summary-item accent' : 'summary-item'}>
      <span>{label}</span>
      <strong>{value || '—'}</strong>
    </div>
  )
}

function formatPeriod(invoice, selectedPeriod) {
  return selectedPeriod
}

export default InvoiceDetailsModal
