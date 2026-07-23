import Button from './ui/Button.jsx'
import Badge from './ui/Badge.jsx'

function BillingOverview({
  selectedPeriod,
  healthStatus,
  importSummary,
  invoicesCount,
  isImporting,
  isGenerating,
  onImport,
  onGenerate,
}) {
  const status = getOverviewStatus({ healthStatus, importSummary, invoicesCount })
  const imported = Boolean(importSummary)
  const generated = invoicesCount > 0
  const backendReady = healthStatus === 'connected'

  return (
    <section className="overview-panel" aria-labelledby="billing-overview-title">
      <div className="overview-content">
        <div className="overview-copy">
          <Badge tone={status.tone}>{status.label}</Badge>
          <h2 id="billing-overview-title">Billing Overview</h2>
          <p>
            Import source CSV files, generate invoices for the selected billing period, then review persisted documents
            and line-level charges.
          </p>
        </div>

        <div className="overview-actions">
          <Button type="button" onClick={onImport} disabled={!backendReady || isImporting}>
            {isImporting ? 'Importing' : imported ? 'Re-import CSV files' : 'Import CSV files'}
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={onGenerate}
            disabled={!backendReady || isGenerating || !imported}
          >
            {isGenerating ? 'Generating' : 'Generate invoices'}
          </Button>
        </div>
      </div>

      <div className="overview-meta" aria-label="Billing workflow status">
        <div className="overview-period">
          <span>Selected period</span>
          <strong>{selectedPeriod}</strong>
        </div>

        <div className="workflow-strip">
          <WorkflowStep label="Ready to import" active={!imported && !generated && backendReady} complete={imported} />
          <WorkflowStep label="Data imported" active={imported && !generated && backendReady} complete={imported} />
          <WorkflowStep label="Invoices generated" active={generated && backendReady} complete={generated} />
        </div>

        <div className="overview-accent" aria-hidden="true">
          <span className="document-stack document-stack-one" />
          <span className="document-stack document-stack-two" />
          <span className="document-stack document-stack-three" />
        </div>
      </div>
    </section>
  )
}

function WorkflowStep({ label, active, complete }) {
  return (
    <div className={`workflow-step ${active ? 'active' : ''} ${complete ? 'complete' : ''}`.trim()}>
      <span aria-hidden="true" />
      <strong>{label}</strong>
    </div>
  )
}

function getOverviewStatus({ healthStatus, importSummary, invoicesCount }) {
  if (healthStatus === 'checking') {
    return { label: 'Checking backend', tone: 'warning' }
  }
  if (healthStatus === 'offline') {
    return { label: 'Backend unavailable', tone: 'danger' }
  }
  if (healthStatus !== 'connected') {
    return { label: 'Backend status unknown', tone: 'warning' }
  }
  if (invoicesCount > 0) {
    return { label: 'Invoices generated', tone: 'success' }
  }
  if (importSummary) {
    return { label: 'Ready to generate', tone: 'primary' }
  }
  return { label: 'Ready to import', tone: 'warning' }
}

export default BillingOverview
