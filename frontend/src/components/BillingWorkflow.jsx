import ImportCard from './ImportCard.jsx'
import GenerateInvoiceCard from './GenerateInvoiceCard.jsx'

function BillingWorkflow({
  inputDirectory,
  importSummary,
  importStatus,
  importError,
  generateStatus,
  generateError,
  isImporting,
  isGenerating,
  selectedPeriod,
  months,
  years,
  month,
  year,
  isBackendAvailable,
  invoicesCount,
  onImport,
  onGenerate,
  onMonthChange,
  onYearChange,
  onPreviousPeriod,
  onNextPeriod,
  onDismissImport,
  onDismissGenerate,
}) {
  const hasImportedData = Boolean(importSummary)
  const hasInvoices = invoicesCount > 0
  const steps = [
    getStepState('Import source data', {
      failed: Boolean(importError),
      active: !hasImportedData,
      complete: hasImportedData,
    }),
    getStepState('Configure billing period', {
      active: hasImportedData && !hasInvoices,
      complete: hasImportedData,
    }),
    getStepState('Generate and review invoices', {
      failed: Boolean(generateError),
      active: hasImportedData && !hasInvoices,
      complete: hasInvoices,
    }),
  ]

  return (
    <section className="workflow-panel" aria-labelledby="billing-workflow-title">
      <div className="workflow-header">
        <div>
          <p className="eyebrow">Guided workflow</p>
          <h2 id="billing-workflow-title">Billing run setup</h2>
          <p>Move through the billing run in order: import source data, confirm the period, then generate invoices.</p>
        </div>
      </div>

      <ol className="workflow-stepper" aria-label="Billing workflow steps">
        {steps.map((step, index) => (
          <li className={`workflow-stepper-item ${step.state}`} key={step.label}>
            <span className="workflow-step-index">{index + 1}</span>
            <div>
              <strong>{step.label}</strong>
              <span>{step.copy}</span>
            </div>
          </li>
        ))}
      </ol>

      <div className="workflow-grid">
        <ImportCard
          inputDirectory={inputDirectory}
          summary={importSummary}
          status={importStatus}
          error={importError}
          isLoading={isImporting}
          isBackendAvailable={isBackendAvailable}
          onImport={onImport}
          onDismiss={onDismissImport}
        />

        <GenerateInvoiceCard
          months={months}
          years={years}
          month={month}
          year={year}
          selectedPeriod={selectedPeriod}
          hasImportedData={hasImportedData}
          hasInvoices={hasInvoices}
          status={generateStatus}
          error={generateError}
          isLoading={isGenerating}
          isBackendAvailable={isBackendAvailable}
          onMonthChange={onMonthChange}
          onYearChange={onYearChange}
          onPreviousPeriod={onPreviousPeriod}
          onNextPeriod={onNextPeriod}
          onGenerate={onGenerate}
          onDismiss={onDismissGenerate}
        />
      </div>
    </section>
  )
}

function getStepState(label, { failed, active, complete }) {
  if (failed) {
    return { label, state: 'failed', copy: 'Needs attention' }
  }
  if (complete) {
    return { label, state: 'complete', copy: 'Complete' }
  }
  if (active) {
    return { label, state: 'active', copy: 'Active' }
  }
  return { label, state: 'pending', copy: 'Pending' }
}

export default BillingWorkflow
