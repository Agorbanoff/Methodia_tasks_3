import Alert from './Alert.jsx'
import LoadingSpinner from './LoadingSpinner.jsx'
import Button from './ui/Button.jsx'
import Card from './ui/Card.jsx'
import ErrorAlert from './ui/ErrorAlert.jsx'

function ImportCard({ inputDirectory, summary, status, error, isLoading, isBackendAvailable, onImport, onDismiss }) {
  return (
    <Card className="workflow-card import-card">
      <div className="card-header">
        <div>
          <p className="eyebrow">Step 1</p>
          <h2>Import source data</h2>
          <p>Load the expected CSV source files from the backend configured input directory.</p>
        </div>
      </div>

      <div className="source-panel">
        <div>
          <span>Input directory</span>
          <strong>{inputDirectory || 'Unavailable until backend health responds'}</strong>
        </div>
        <ul className="source-list" aria-label="Expected CSV source categories">
          <li>Users / customers</li>
          <li>Meter readings</li>
          <li>Price lists</li>
        </ul>
      </div>

      <Button type="button" onClick={onImport} disabled={isLoading || !isBackendAvailable}>
        {isLoading ? <LoadingSpinner label="Importing source data" /> : 'Import CSV Files'}
      </Button>

      {isLoading && (
        <div className="progress-note" role="status">
          <span className="progress-bar" aria-hidden="true" />
          Reading configured CSV files and storing validated rows.
        </div>
      )}

      {status && (
        <Alert type="success" title="Import completed" onDismiss={onDismiss}>
          {status}
        </Alert>
      )}
      <ErrorAlert error={error} onRetry={onImport} onDismiss={onDismiss} />

      <div className="mini-metrics">
        <Metric label="Users" value={summary?.importedUsers ?? '—'} />
        <Metric label="Readings" value={summary?.importedReadings ?? '—'} />
        <Metric label="Prices" value={summary?.importedPrices ?? '—'} />
        <Metric label="Skipped" value={summary?.skippedDuplicates ?? '—'} />
      </div>

      {summary?.errors?.length > 0 && (
        <Alert type="info" title="Import warnings">
          {summary.errors.join('; ')}
        </Alert>
      )}
    </Card>
  )
}

function Metric({ label, value }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

export default ImportCard
