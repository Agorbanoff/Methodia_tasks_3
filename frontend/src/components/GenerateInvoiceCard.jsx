import Alert from './Alert.jsx'
import LoadingSpinner from './LoadingSpinner.jsx'
import Button from './ui/Button.jsx'
import Card from './ui/Card.jsx'
import ErrorAlert from './ui/ErrorAlert.jsx'
import FormField from './ui/FormField.jsx'

function GenerateInvoiceCard({
  months,
  years,
  month,
  year,
  selectedPeriod,
  hasImportedData,
  hasInvoices,
  status,
  error,
  isLoading,
  isBackendAvailable,
  onMonthChange,
  onYearChange,
  onPreviousPeriod,
  onNextPeriod,
  onGenerate,
  onDismiss,
}) {
  const yearIsSupported = Number(year) >= 1900 && Number(year) <= 2100
  const monthIsSupported = Number(month) >= 1 && Number(month) <= 12
  const periodIsValid = yearIsSupported && monthIsSupported
  const canGenerate = isBackendAvailable && hasImportedData && periodIsValid && !isLoading
  const isFirstSupportedPeriod = Number(year) === 1900 && Number(month) === 1
  const isLastSupportedPeriod = Number(year) === 2100 && Number(month) === 12

  function handleSubmit(event) {
    event.preventDefault()
    if (canGenerate) {
      onGenerate()
    }
  }

  return (
    <Card className="workflow-card generate-card">
      <div className="period-config-section">
        <div className="card-header">
          <div>
            <p className="eyebrow">Step 2</p>
            <h2>Configure billing period</h2>
            <p>Select the month and year for the invoice generation run.</p>
          </div>
        </div>

        <form className="period-form" onSubmit={handleSubmit}>
          <div className="period-controls">
            <Button variant="secondary" type="button" onClick={onPreviousPeriod} disabled={isLoading || isFirstSupportedPeriod}>
              Previous
            </Button>

            <FormField label="Month">
              <select value={month} onChange={(event) => onMonthChange(Number(event.target.value))}>
                {months.map((monthOption) => (
                  <option key={monthOption.value} value={monthOption.value}>
                    {monthOption.label}
                  </option>
                ))}
              </select>
            </FormField>

            <FormField label="Year" helperText="Supported range: 1900-2100">
              <select value={year} onChange={(event) => onYearChange(Number(event.target.value))}>
                {years.map((yearOption) => (
                  <option key={yearOption} value={yearOption}>
                    {yearOption}
                  </option>
                ))}
              </select>
            </FormField>

            <Button variant="secondary" type="button" onClick={onNextPeriod} disabled={isLoading || isLastSupportedPeriod}>
              Next
            </Button>
          </div>

          <div className="selected-period-summary">
            <span>Selected billing period</span>
            <strong>{selectedPeriod}</strong>
            {!periodIsValid && <p>Choose a month from 1-12 and year from 1900-2100.</p>}
          </div>
        </form>
      </div>

      <div className="generation-section">
        <div className="card-header">
          <div>
            <p className="eyebrow">Step 3</p>
            <h2>Generate and review invoices</h2>
            <p>Confirm readiness, generate invoice documents, then review the refreshed invoice register.</p>
          </div>
        </div>

        <div className="generation-confirmation">
          <span>Generation period</span>
          <strong>{selectedPeriod}</strong>
        </div>

        <ul className="readiness-list" aria-label="Generation readiness checklist">
          <ChecklistItem complete={isBackendAvailable} label="Backend API is reachable" />
          <ChecklistItem complete={hasImportedData} label="Source data has been imported in this session" />
          <ChecklistItem complete={periodIsValid} label="Billing period is supported" />
          <ChecklistItem complete={!isLoading} label="No generation request is already running" />
        </ul>

        {hasInvoices && (
          <Alert type="info" title="Existing invoices loaded">
            This selected view already has invoices loaded. The backend will skip existing invoices for the same period.
          </Alert>
        )}

        <Button type="button" onClick={onGenerate} disabled={!canGenerate}>
          {isLoading ? <LoadingSpinner label="Generating" /> : 'Generate invoices'}
        </Button>
      </div>

      {status && (
        <Alert type="success" title="Generation completed" onDismiss={onDismiss}>
          {status}
        </Alert>
      )}
      <ErrorAlert error={error} onRetry={canGenerate ? onGenerate : null} onDismiss={onDismiss} />
    </Card>
  )
}

function ChecklistItem({ complete, label }) {
  return (
    <li className={complete ? 'complete' : 'pending'}>
      <span aria-hidden="true">{complete ? '✓' : '•'}</span>
      {label}
    </li>
  )
}

export default GenerateInvoiceCard
